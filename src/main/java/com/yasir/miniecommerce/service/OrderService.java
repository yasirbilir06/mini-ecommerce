package com.yasir.miniecommerce.service;

import com.yasir.miniecommerce.dto.OrderItemRequest;
import com.yasir.miniecommerce.dto.OrderRequest;
import com.yasir.miniecommerce.dto.OrderResponse;
import com.yasir.miniecommerce.dto.UpdateOrderStatusRequest;
import com.yasir.miniecommerce.exception.*;
import com.yasir.miniecommerce.mapper.OrderMapper;
import com.yasir.miniecommerce.model.*;
import com.yasir.miniecommerce.repository.OrderRepository;
import com.yasir.miniecommerce.repository.ProductRepository;
import com.yasir.miniecommerce.repository.UserRepository;
import jakarta.transaction.Transactional;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

@Service
public class OrderService {

    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final OrderMapper orderMapper;

    public OrderService(
            OrderRepository orderRepository,
            ProductRepository productRepository,
            UserRepository userRepository,
            OrderMapper orderMapper) {

        this.orderRepository = orderRepository;
        this.productRepository = productRepository;
        this.userRepository = userRepository;
        this.orderMapper = orderMapper;
    }

    // Sipariş oluşturur ve stokları azaltır.
    @Transactional
    public OrderResponse createOrder(OrderRequest request) {

        Order order = new Order();
        order.setStatus(OrderStatus.PENDING);

        String email = SecurityContextHolder
                .getContext()
                .getAuthentication()
                .getName();

        User user = userRepository.findByEmail(email)
                .orElseThrow(() ->
                        new UserNotFoundException(
                                "Kullanıcı bulunamadı: " + email
                        ));

        order.setUser(user);

        BigDecimal totalPrice = BigDecimal.ZERO;

        for (OrderItemRequest itemRequest : request.getItems()) {

            Product product = productRepository.findById(
                            itemRequest.getProductId())
                    .orElseThrow(() ->
                            new ProductNotFoundException(
                                    "Ürün bulunamadı: "
                                            + itemRequest.getProductId()
                            ));

            if (itemRequest.getQuantity() > product.getStock()) {
                throw new InsufficientStockException(
                        "Yeterli stok yok: " + product.getName()
                );
            }

            // Stok miktarını azalt.
            product.setStock(
                    product.getStock() - itemRequest.getQuantity()
            );

            OrderItem orderItem = new OrderItem();

            orderItem.setProduct(product);
            orderItem.setQuantity(itemRequest.getQuantity());
            orderItem.setPrice(product.getPrice());
            orderItem.setOrder(order);

            order.getOrderItems().add(orderItem);

            // Ürün fiyatı x sipariş edilen miktar.
            BigDecimal itemTotal = product.getPrice()
                    .multiply(
                            BigDecimal.valueOf(itemRequest.getQuantity())
                    );

            totalPrice = totalPrice.add(itemTotal);
        }

        order.setTotalPrice(totalPrice);

        Order savedOrder = orderRepository.save(order);

        return orderMapper.toResponse(savedOrder);
    }

    // Giriş yapan kullanıcının tüm siparişlerini getirir.
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

    // Kullanıcının kendi siparişini iptal eder ve stokları geri yükler.
    @Transactional
    public OrderResponse cancelMyOrder(Long id) {

        String email = SecurityContextHolder
                .getContext()
                .getAuthentication()
                .getName();

        // Yalnızca giriş yapan kullanıcıya ait sipariş bulunur.
        Order order = orderRepository.findByIdAndUserEmail(id, email)
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

        // Siparişteki ürünlerin stoklarını geri yükle.
        for (OrderItem orderItem : order.getOrderItems()) {

            Product product = orderItem.getProduct();

            product.setStock(
                    product.getStock() + orderItem.getQuantity()
            );
        }

        order.setStatus(OrderStatus.CANCELLED);

        Order cancelledOrder = orderRepository.save(order);

        return orderMapper.toResponse(cancelledOrder);
    }

    // Yönetici sipariş durumunu değiştirir.
    @Transactional
    public OrderResponse updateOrderStatus(
            Long id,
            UpdateOrderStatusRequest request) {

        Order order = orderRepository.findById(id)
                .orElseThrow(() ->
                        new OrderNotFoundException(
                                "Sipariş bulunamadı: " + id
                        ));

        OrderStatus currentStatus = order.getStatus();
        OrderStatus newStatus = request.getStatus();

        // İzin verilen sipariş durumu geçişlerini kontrol et.
        boolean validTransition = switch (currentStatus) {

            case PENDING ->
                    newStatus == OrderStatus.CONFIRMED
                            || newStatus == OrderStatus.CANCELLED;

            case CONFIRMED ->
                    newStatus == OrderStatus.SHIPPED
                            || newStatus == OrderStatus.CANCELLED;

            case SHIPPED ->
                    newStatus == OrderStatus.DELIVERED;

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

            for (OrderItem orderItem : order.getOrderItems()) {

                Product product = orderItem.getProduct();

                product.setStock(
                        product.getStock() + orderItem.getQuantity()
                );
            }
        }

        order.setStatus(newStatus);

        Order updatedOrder = orderRepository.save(order);

        return orderMapper.toResponse(updatedOrder);
    }
}