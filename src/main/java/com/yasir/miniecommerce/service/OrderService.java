    package com.yasir.miniecommerce.service;

    import com.stripe.exception.ApiKeyMissingException;
    import com.stripe.exception.AuthenticationException;
    import com.stripe.exception.InvalidRequestException;
    import com.stripe.exception.StripeException;
    import com.stripe.model.PaymentIntent;
    import com.yasir.miniecommerce.dto.OrderRequest;
    import com.yasir.miniecommerce.dto.OrderResponse;
    import com.yasir.miniecommerce.dto.PaymentIntentResponse;
    import com.yasir.miniecommerce.dto.UpdateOrderStatusRequest;
    import com.yasir.miniecommerce.exception.IdempotencyKeyConflictException;
    import com.yasir.miniecommerce.exception.InvalidIdempotencyKeyException;
    import com.yasir.miniecommerce.exception.InvalidOrderStatusException;
    import com.yasir.miniecommerce.exception.OrderNotFoundException;
    import com.yasir.miniecommerce.exception.PaymentStateConflictException;
    import com.yasir.miniecommerce.exception.ProductNotFoundException;
    import com.yasir.miniecommerce.exception.UserNotFoundException;
    import com.yasir.miniecommerce.mapper.OrderMapper;
    import com.yasir.miniecommerce.model.Order;
    import com.yasir.miniecommerce.model.OrderItem;
    import com.yasir.miniecommerce.model.OrderStatus;
    import com.yasir.miniecommerce.model.PaymentStatus;
    import com.yasir.miniecommerce.model.Product;
    import com.yasir.miniecommerce.model.User;
    import com.yasir.miniecommerce.repository.OrderRepository;
    import com.yasir.miniecommerce.repository.ProductRepository;
    import com.yasir.miniecommerce.repository.UserRepository;
    import org.springframework.dao.DataIntegrityViolationException;
    import org.springframework.security.core.context.SecurityContextHolder;
    import org.springframework.stereotype.Service;
    import org.springframework.transaction.annotation.Transactional;

    import java.nio.charset.StandardCharsets;
    import java.security.MessageDigest;
    import java.security.NoSuchAlgorithmException;
    import java.util.HashMap;
    import java.util.HexFormat;
    import java.util.List;
    import java.util.Locale;
    import java.util.Map;
    import java.util.Optional;

        @Service
        public class OrderService {

            private final OrderRepository orderRepository;
            private final ProductRepository productRepository;
            private final UserRepository userRepository;
            private final OrderMapper orderMapper;
            private final OrderCreationTransaction orderCreationTransaction;
            private final StripePaymentService stripePaymentService;
            private final PaymentIntentTransaction paymentIntentTransaction;

            public OrderService(
                    OrderRepository orderRepository,
                    ProductRepository productRepository,
                    UserRepository userRepository,
                    OrderMapper orderMapper,
                    OrderCreationTransaction orderCreationTransaction,
                    StripePaymentService stripePaymentService,
                    PaymentIntentTransaction paymentIntentTransaction) {

                this.orderRepository = orderRepository;
                this.productRepository = productRepository;
                this.userRepository = userRepository;
                this.orderMapper = orderMapper;
                this.orderCreationTransaction = orderCreationTransaction;
                this.stripePaymentService = stripePaymentService;
                this.paymentIntentTransaction = paymentIntentTransaction;
            }

            // Replays a committed request or delegates the atomic write to a separate transaction bean.
            public OrderResponse createOrder(OrderRequest request, String idempotencyKey) {
                idempotencyKey = normalizeIdempotencyKey(idempotencyKey);

                String email = SecurityContextHolder
                        .getContext()
                        .getAuthentication()
                        .getName();

                User user = userRepository.findByEmail(email)
                        .orElseThrow(() ->
                                new UserNotFoundException(
                                        "Kullanıcı bulunamadı: " + email
                                ));

                String requestFingerprint = fingerprint(request);
                Optional<Order> existingOrder = orderRepository.findByUserIdAndIdempotencyKey(
                        user.getId(), idempotencyKey
                );
                if (existingOrder.isPresent()) {
                    return replayOrReject(existingOrder.get(), requestFingerprint);
                }

                try {
                    return orderCreationTransaction.createOrder(
                            email, request, idempotencyKey, requestFingerprint
                    );
                } catch (DataIntegrityViolationException exception) {
                    // A concurrent request may have committed the unique key while
                    // this transaction was inserting its reservation. Its transaction
                    // is now rolled back, so this lookup runs outside that transaction.
                    Optional<Order> concurrentlyCreated = orderRepository.findByUserIdAndIdempotencyKey(
                            user.getId(), idempotencyKey
                    );
                    if (concurrentlyCreated.isEmpty()) {
                        throw exception;
                    }
                    return replayOrReject(concurrentlyCreated.get(), requestFingerprint);
                }
            }

            private OrderResponse replayOrReject(Order order, String requestFingerprint) {
                if (!requestFingerprint.equals(order.getRequestFingerprint())) {
                    throw new IdempotencyKeyConflictException(
                            "Idempotency-Key daha önce farklı bir sipariş içeriğiyle kullanıldı."
                    );
                }
                return orderMapper.toResponse(order);
            }

            private String normalizeIdempotencyKey(String idempotencyKey) {
                if (idempotencyKey == null || idempotencyKey.isEmpty()
                        || idempotencyKey.length() > 128
                        || idempotencyKey.chars().anyMatch(character -> character < 33 || character > 126)) {
                    throw new InvalidIdempotencyKeyException(
                            "Idempotency-Key zorunludur; 1-128 görünür ASCII karakter içermelidir."
                    );
                }
                return idempotencyKey.toLowerCase(Locale.ROOT);
            }

            private String fingerprint(OrderRequest request) {
                StringBuilder canonicalRequest = new StringBuilder();
                request.getItems().forEach(item -> canonicalRequest
                        .append(item.getProductId())
                        .append(':')
                        .append(item.getQuantity())
                        .append(';'));
                try {
                    byte[] digest = MessageDigest.getInstance("SHA-256")
                            .digest(canonicalRequest.toString().getBytes(StandardCharsets.UTF_8));
                    return HexFormat.of().formatHex(digest);
                } catch (NoSuchAlgorithmException exception) {
                    throw new IllegalStateException("SHA-256 kullanılamıyor.", exception);
                }
            }

            // Giriş yapan kullanıcının tüm siparişlerini getirir.
            @Transactional(readOnly = true)
            public List<OrderResponse> getMyOrders() {

                String email = SecurityContextHolder
                        .getContext()
                        .getAuthentication()
                        .getName();

                List<Order> orders = orderRepository.findByUserEmail(email);

                return orders.stream()
                        .map(orderMapper::toResponse)
                        .toList();
            }

            // Kullanıcının yalnızca kendi siparişini getirir.
            public OrderResponse getMyOrderById(Long id) {

                String email = SecurityContextHolder
                        .getContext()
                        .getAuthentication()
                        .getName();

                Order order = orderRepository.findByIdAndUserEmail(id, email)
                        .orElseThrow(() ->
                                new OrderNotFoundException(
                                        "Sipariş bulunamadı: " + id
                                ));

                return orderMapper.toResponse(order);
            }

            /**
             * Creates or safely reuses a Stripe PaymentIntent for the authenticated
             * user's order. Database locks are held only inside PaymentIntentTransaction.
             */
            public PaymentIntentResponse createPaymentIntent(Long orderId)
                    throws StripeException {

                String email = SecurityContextHolder
                        .getContext()
                        .getAuthentication()
                        .getName();

                PaymentIntentTransaction.PaymentIntentReservation reservation =
                        paymentIntentTransaction.reservePaymentIntent(orderId, email);

                PaymentIntent paymentIntent;
                if (reservation.hasPersistedPaymentIntent()) {
                    paymentIntent = stripePaymentService.retrievePaymentIntent(
                            reservation.existingPaymentIntentId()
                    );
                } else {
                    try {
                        paymentIntent = stripePaymentService.createPaymentIntent(
                                reservation.amountInCents(),
                                reservation.currency(),
                                String.valueOf(reservation.orderId()),
                                reservation.stripeIdempotencyKey()
                        );
                    } catch (InvalidRequestException | AuthenticationException exception) {
                        paymentIntentTransaction.markDefinitiveCreationFailure(orderId, email);
                        throw exception;
                    } catch (ApiKeyMissingException exception) {
                        paymentIntentTransaction.markDefinitiveCreationFailure(orderId, email);
                        throw exception;
                    }

                    reservation = paymentIntentTransaction.recordPaymentIntent(
                            orderId,
                            email,
                            paymentIntent.getId()
                    );
                }

                return toPaymentIntentResponse(paymentIntent, reservation);
            }

            public void handlePaymentIntentSucceeded(PaymentIntent paymentIntent) {
                Long orderId = extractOrderId(paymentIntent);

                paymentIntentTransaction.confirmPaymentIntentSucceeded(
                        orderId,
                        paymentIntent.getId(),
                        paymentIntent.getAmount(),
                        paymentIntent.getCurrency()
                );
            }

            // Kullanıcının kendi siparişini iptal eder ve stokları geri yükler.
            @Transactional
            public OrderResponse cancelMyOrder(Long id) {

                String email = SecurityContextHolder
                        .getContext()
                        .getAuthentication()
                        .getName();

                // Yalnızca giriş yapan kullanıcıya ait sipariş bulunur.
                Order order = orderRepository.findByIdAndUserEmailWithLock(id, email)
                        .orElseThrow(() ->
                                new OrderNotFoundException(
                                        "Sipariş bulunamadı: " + id
                                ));

                OrderStatus currentStatus = order.getStatus();

                // Yalnızca PENDING ve CONFIRMED siparişler iptal edilebilir.
                if (currentStatus != OrderStatus.PENDING
                        && currentStatus != OrderStatus.CONFIRMED) {

                    throw new InvalidOrderStatusException(
                            "Bu sipariş iptal edilemez. Mevcut durum: "
                                    + currentStatus
                    );
                }

                assertOrderCanBeCancelled(order);

                // Siparişteki ürünlerin stoklarını geri yükle.
                restoreStock(order);

                order.setStatus(OrderStatus.CANCELLED);

                Order cancelledOrder = orderRepository.save(order);

                return orderMapper.toResponse(cancelledOrder);
            }

            // Yönetici sipariş durumunu değiştirir.
            @Transactional
            public OrderResponse updateOrderStatus(
                    Long id,
                    UpdateOrderStatusRequest request) {

                Order order = orderRepository.findByIdWithLock(id)
                        .orElseThrow(() ->
                                new OrderNotFoundException(
                                        "Sipariş bulunamadı: " + id
                                ));

                OrderStatus currentStatus = order.getStatus();
                OrderStatus newStatus = request.getStatus();

                // İzin verilen sipariş durumu geçişlerini kontrol et.
                boolean validTransition = switch (currentStatus) {

                    case PENDING -> newStatus == OrderStatus.CONFIRMED
                            || newStatus == OrderStatus.CANCELLED;

                    case CONFIRMED -> newStatus == OrderStatus.SHIPPED
                            || newStatus == OrderStatus.CANCELLED;

                    case SHIPPED -> newStatus == OrderStatus.DELIVERED;

                    case DELIVERED, CANCELLED -> false;
                };

                if (!validTransition) {
                    throw new InvalidOrderStatusException(
                            "Sipariş durumu " + currentStatus
                                    + " durumundan " + newStatus
                                    + " durumuna değiştirilemez."
                    );
                }

                // Yönetici siparişi iptal ederse stokları geri yükle.
                if (newStatus == OrderStatus.CANCELLED) {

                    assertOrderCanBeCancelled(order);

                    restoreStock(order);
                }

                order.setStatus(newStatus);

                Order updatedOrder = orderRepository.save(order);

                return orderMapper.toResponse(updatedOrder);
            }

            private PaymentIntentResponse toPaymentIntentResponse(
                    PaymentIntent paymentIntent,
                    PaymentIntentTransaction.PaymentIntentReservation reservation) {

                PaymentIntentResponse response = new PaymentIntentResponse();
                response.setPaymentIntentId(paymentIntent.getId());
                response.setClientSecret(paymentIntent.getClientSecret());
                response.setAmount(reservation.amount());
                response.setCurrency(reservation.currency());
                response.setPaymentStatus(reservation.paymentStatus());

                return response;
            }

            private Long extractOrderId(PaymentIntent paymentIntent) {
                if (paymentIntent == null || paymentIntent.getMetadata() == null) {
                    throw new IllegalArgumentException("Webhook payment data is invalid.");
                }

                String orderIdValue = paymentIntent.getMetadata().get("orderId");
                if (orderIdValue == null || orderIdValue.isBlank()) {
                    throw new IllegalArgumentException("Webhook payment data is invalid.");
                }

                try {
                    long orderId = Long.parseLong(orderIdValue);
                    if (orderId <= 0) {
                        throw new IllegalArgumentException("Webhook payment data is invalid.");
                    }
                    return orderId;
                } catch (NumberFormatException exception) {
                    throw new IllegalArgumentException("Webhook payment data is invalid.");
                }
            }

            private void assertOrderCanBeCancelled(Order order) {
                if (order.getPaymentStatus() == PaymentStatus.PROCESSING) {
                    throw new PaymentStateConflictException(
                            "Ödeme işlemi devam eden sipariş iptal edilemez."
                    );
                }
            }

            private void restoreStock(Order order) {
                Map<Long, Product> productsById = lockProductsInAscendingOrder(
                        order.getOrderItems().stream()
                                .map(orderItem -> orderItem.getProduct().getId())
                                .toList()
                );

                for (OrderItem orderItem : order.getOrderItems()) {
                    Product product = productsById.get(orderItem.getProduct().getId());

                    product.setStock(
                            product.getStock() + orderItem.getQuantity()
                    );
                }
            }

            private Map<Long, Product> lockProductsInAscendingOrder(List<Long> productIds) {
                Map<Long, Product> productsById = new HashMap<>();

                productIds.stream()
                        .distinct()
                        .sorted()
                        .forEach(productId -> {
                            Product product = productRepository.findByIdWithLock(productId)
                                    .orElseThrow(() ->
                                            new ProductNotFoundException(
                                                    "Ürün bulunamadı: " + productId
                                            )
                                    );
                            productsById.put(productId, product);
                        });

                return productsById;
            }
        }
