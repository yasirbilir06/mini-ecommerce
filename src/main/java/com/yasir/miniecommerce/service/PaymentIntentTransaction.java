package com.yasir.miniecommerce.service;

import com.yasir.miniecommerce.exception.OrderNotFoundException;
import com.yasir.miniecommerce.exception.PaymentStateConflictException;
import com.yasir.miniecommerce.model.Order;
import com.yasir.miniecommerce.model.OrderItem;
import com.yasir.miniecommerce.model.OrderStatus;
import com.yasir.miniecommerce.model.PaymentStatus;
import com.yasir.miniecommerce.repository.OrderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/**
 * Keeps database transactions short while a PaymentIntent is created outside
 * the database lock.
 */
@Service
public class PaymentIntentTransaction {

    private static final String EUR = "EUR";
    private static final String IDEMPOTENCY_KEY_PREFIX = "order-payment-intent-";
    private static final BigDecimal CENT_MULTIPLIER = new BigDecimal("100");

    private final OrderRepository orderRepository;

    public PaymentIntentTransaction(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    /**
     * Reserves the payment attempt before the caller contacts Stripe. A retry
     * without a persisted PaymentIntent ID deliberately receives the same
     * server-generated Stripe idempotency key.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public PaymentIntentReservation reservePaymentIntent(
            Long orderId,
            String email) {

        Order order = findOwnedOrderWithLock(orderId, email);
        assertPaymentCanStart(order);
        PaymentAmount paymentAmount = calculateVerifiedAmount(order);

        String existingPaymentIntentId = order.getStripePaymentIntentId();
        if (existingPaymentIntentId == null) {
            order.setPaymentStatus(PaymentStatus.PROCESSING);
            orderRepository.save(order);
        }

        return new PaymentIntentReservation(
                order.getId(),
                existingPaymentIntentId,
                stripeIdempotencyKey(order.getId()),
                paymentAmount.amount(),
                paymentAmount.amountInCents(),
                EUR,
                order.getPaymentStatus()
        );
    }

    /**
     * Persists Stripe's result after the remote call completed. Repeating the
     * same result is safe; a different result for one order is rejected.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public PaymentIntentReservation recordPaymentIntent(
            Long orderId,
            String email,
            String paymentIntentId) {

        if (paymentIntentId == null || paymentIntentId.isBlank()) {
            throw new IllegalArgumentException("PaymentIntent ID cannot be empty.");
        }

        Order order = findOwnedOrderWithLock(orderId, email);
        assertPaymentCanStart(order);
        PaymentAmount paymentAmount = calculateVerifiedAmount(order);

        String existingPaymentIntentId = order.getStripePaymentIntentId();
        if (existingPaymentIntentId != null
                && !existingPaymentIntentId.equals(paymentIntentId)) {
            throw new PaymentStateConflictException(
                    "Sipariş için farklı bir ödeme girişimi zaten kaydedilmiş."
            );
        }

        if (existingPaymentIntentId == null) {
            order.setStripePaymentIntentId(paymentIntentId);
            order.setPaymentCurrency(EUR);
            order.setPaymentStatus(PaymentStatus.PROCESSING);
            orderRepository.save(order);
        }

        return new PaymentIntentReservation(
                order.getId(),
                order.getStripePaymentIntentId(),
                stripeIdempotencyKey(order.getId()),
                paymentAmount.amount(),
                paymentAmount.amountInCents(),
                EUR,
                order.getPaymentStatus()
        );
    }

    /**
     * This method must be used only when the caller knows that Stripe did not
     * create a PaymentIntent. Ambiguous network failures stay PROCESSING so a
     * retry can safely reuse the same Stripe idempotency key.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markDefinitiveCreationFailure(Long orderId, String email) {

        Order order = findOwnedOrderWithLock(orderId, email);

        if (order.getStripePaymentIntentId() == null
                && order.getPaymentStatus() != PaymentStatus.PAID
                && order.getStatus() != OrderStatus.CANCELLED) {
            order.setPaymentStatus(PaymentStatus.FAILED);
            orderRepository.save(order);
        }
    }

    /**
     * Applies a verified successful Stripe payment without making a remote
     * Stripe call while the order row is locked.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void confirmPaymentIntentSucceeded(
            Long orderId,
            String paymentIntentId,
            Long stripeAmountInCents,
            String stripeCurrency) {

        if (paymentIntentId == null || paymentIntentId.isBlank()
                || stripeAmountInCents == null
                || stripeAmountInCents <= 0
                || stripeCurrency == null
                || stripeCurrency.isBlank()) {
            throw new IllegalArgumentException("Webhook payment data is invalid.");
        }

        Order order = orderRepository.findByIdWithLock(orderId)
                .orElseThrow(() -> new OrderNotFoundException(
                        "Sipariş bulunamadı: " + orderId
                ));

        if (order.getStatus() == OrderStatus.CANCELLED) {
            throw new PaymentStateConflictException(
                    "İptal edilmiş sipariş için ödeme onayı işlenemez."
            );
        }

        PaymentAmount expectedAmount = calculateVerifiedAmount(order);
        if (expectedAmount.amountInCents() != stripeAmountInCents
                || !EUR.equalsIgnoreCase(stripeCurrency)) {
            throw new PaymentStateConflictException(
                    "Ödeme bilgileri sipariş ile eşleşmiyor."
            );
        }

        if (order.getPaymentCurrency() != null
                && !EUR.equalsIgnoreCase(order.getPaymentCurrency())) {
            throw new PaymentStateConflictException(
                    "Siparişin ödeme para birimi geçersiz."
            );
        }

        String existingPaymentIntentId = order.getStripePaymentIntentId();
        if (existingPaymentIntentId != null
                && !existingPaymentIntentId.equals(paymentIntentId)) {
            throw new PaymentStateConflictException(
                    "Ödeme girişimi sipariş ile eşleşmiyor."
            );
        }

        if (order.getPaymentStatus() == PaymentStatus.PAID) {

            if (existingPaymentIntentId == null
                    || !existingPaymentIntentId.equals(paymentIntentId)) {
                throw new PaymentStateConflictException(
                        "Ödenmiş siparişin ödeme girişimi eşleşmiyor."
                );
            }

            if (order.getStatus() != OrderStatus.CONFIRMED) {
                throw new PaymentStateConflictException(
                        "Ödeme yapılmış siparişin durumu geçersiz."
                );
            }

            return;
        }

        if (order.getStatus() != OrderStatus.PENDING
                && order.getStatus() != OrderStatus.CONFIRMED) {
            throw new PaymentStateConflictException(
                    "Siparişin ödeme onayı için durumu uygun değil."
            );
        }

        if (existingPaymentIntentId == null) {
            order.setStripePaymentIntentId(paymentIntentId);
        }
        order.setPaymentCurrency(EUR);
        order.setPaymentStatus(PaymentStatus.PAID);
        order.setStatus(OrderStatus.CONFIRMED);
        orderRepository.save(order);
    }

    private Order findOwnedOrderWithLock(Long orderId, String email) {
        return orderRepository.findByIdAndUserEmailWithLock(orderId, email)
                .orElseThrow(() -> new OrderNotFoundException(
                        "Sipariş bulunamadı: " + orderId
                ));
    }

    private void assertPaymentCanStart(Order order) {
        if (order.getStatus() == OrderStatus.CANCELLED) {
            throw new PaymentStateConflictException(
                    "İptal edilmiş sipariş için ödeme başlatılamaz."
            );
        }

        if (order.getPaymentStatus() == PaymentStatus.PAID) {
            throw new PaymentStateConflictException(
                    "Ödenmiş sipariş için tekrar ödeme başlatılamaz."
            );
        }
    }

    private String stripeIdempotencyKey(Long orderId) {
        return IDEMPOTENCY_KEY_PREFIX + orderId;
    }

    private PaymentAmount calculateVerifiedAmount(Order order) {
        List<OrderItem> orderItems = order.getOrderItems();
        if (orderItems == null || orderItems.isEmpty()) {
            throw new PaymentStateConflictException(
                    "Ödeme için sipariş kalemi bulunamadı."
            );
        }

        BigDecimal calculatedTotal = BigDecimal.ZERO;
        for (OrderItem orderItem : orderItems) {
            if (orderItem.getPrice() == null
                    || orderItem.getPrice().signum() <= 0
                    || orderItem.getQuantity() == null
                    || orderItem.getQuantity() <= 0) {
                throw new PaymentStateConflictException(
                        "Sipariş kalemlerindeki ödeme bilgileri geçersiz."
                );
            }

            calculatedTotal = calculatedTotal.add(
                    orderItem.getPrice().multiply(
                            BigDecimal.valueOf(orderItem.getQuantity())
                    )
            );
        }

        if (order.getTotalPrice() == null
                || calculatedTotal.compareTo(order.getTotalPrice()) != 0) {
            throw new PaymentStateConflictException(
                    "Sipariş toplamı ödeme için doğrulanamadı."
            );
        }

        if (calculatedTotal.signum() <= 0 || calculatedTotal.scale() > 2) {
            throw new PaymentStateConflictException(
                    "Sipariş tutarı EUR için geçerli değil."
            );
        }

        try {
            long amountInCents = calculatedTotal
                    .multiply(CENT_MULTIPLIER)
                    .longValueExact();

            if (amountInCents <= 0) {
                throw new PaymentStateConflictException(
                        "Sipariş tutarı sıfırdan büyük olmalıdır."
                );
            }

            return new PaymentAmount(calculatedTotal, amountInCents);
        } catch (ArithmeticException exception) {
            throw new PaymentStateConflictException(
                    "Sipariş tutarı güvenli biçimde cent değerine dönüştürülemedi."
            );
        }
    }

    public record PaymentIntentReservation(
            Long orderId,
            String existingPaymentIntentId,
            String stripeIdempotencyKey,
            BigDecimal amount,
            long amountInCents,
            String currency,
            PaymentStatus paymentStatus) {

        public boolean hasPersistedPaymentIntent() {
            return existingPaymentIntentId != null;
        }
    }

    private record PaymentAmount(BigDecimal amount, long amountInCents) {
    }
}
