package com.yasir.miniecommerce.service;

import com.yasir.miniecommerce.dto.OrderItemRequest;
import com.yasir.miniecommerce.dto.OrderRequest;
import com.yasir.miniecommerce.dto.OrderResponse;
import com.yasir.miniecommerce.exception.InsufficientStockException;
import com.yasir.miniecommerce.exception.ProductNotFoundException;
import com.yasir.miniecommerce.exception.UserNotFoundException;
import com.yasir.miniecommerce.mapper.OrderMapper;
import com.yasir.miniecommerce.model.Order;
import com.yasir.miniecommerce.model.OrderItem;
import com.yasir.miniecommerce.model.OrderStatus;
import com.yasir.miniecommerce.model.Product;
import com.yasir.miniecommerce.model.User;
import com.yasir.miniecommerce.repository.OrderRepository;
import com.yasir.miniecommerce.repository.ProductRepository;
import com.yasir.miniecommerce.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Writes the idempotency reservation, order, and stock changes atomically. */
@Service
public class OrderCreationTransaction {

    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final OrderMapper orderMapper;

    public OrderCreationTransaction(
            OrderRepository orderRepository,
            ProductRepository productRepository,
            UserRepository userRepository,
            OrderMapper orderMapper) {
        this.orderRepository = orderRepository;
        this.productRepository = productRepository;
        this.userRepository = userRepository;
        this.orderMapper = orderMapper;
    }

    @Transactional
    public OrderResponse createOrder(
            String email,
            OrderRequest request,
            String idempotencyKey,
            String requestFingerprint) {

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UserNotFoundException("Kullanıcı bulunamadı: " + email));

        Order order = new Order();
        order.setUser(user);
        order.setStatus(OrderStatus.PENDING);
        order.setTotalPrice(BigDecimal.ZERO);
        order.setIdempotencyKey(idempotencyKey);
        order.setRequestFingerprint(requestFingerprint);

        // Reserve the unique key before stock locks or changes. Concurrent
        // requests with this user/key serialize on MySQL's unique index here.
        orderRepository.saveAndFlush(order);

        Map<Long, Product> productsById = lockProductsInAscendingOrder(
                request.getItems().stream()
                        .map(OrderItemRequest::getProductId)
                        .toList()
        );

        BigDecimal totalPrice = BigDecimal.ZERO;
        for (OrderItemRequest itemRequest : request.getItems()) {
            Product product = productsById.get(itemRequest.getProductId());
            if (itemRequest.getQuantity() > product.getStock()) {
                throw new InsufficientStockException("Yeterli stok yok: " + product.getName());
            }

            product.setStock(product.getStock() - itemRequest.getQuantity());

            OrderItem orderItem = new OrderItem();
            orderItem.setProduct(product);
            orderItem.setQuantity(itemRequest.getQuantity());
            orderItem.setPrice(product.getPrice());
            orderItem.setOrder(order);
            order.getOrderItems().add(orderItem);

            totalPrice = totalPrice.add(
                    product.getPrice().multiply(BigDecimal.valueOf(itemRequest.getQuantity()))
            );
        }

        order.setTotalPrice(totalPrice);
        return orderMapper.toResponse(orderRepository.save(order));
    }

    private Map<Long, Product> lockProductsInAscendingOrder(List<Long> productIds) {
        Map<Long, Product> productsById = new HashMap<>();

        productIds.stream()
                .distinct()
                .sorted()
                .forEach(productId -> {
                    Product product = productRepository.findByIdWithLock(productId)
                            .orElseThrow(() -> new ProductNotFoundException(
                                    "Ürün bulunamadı: " + productId
                            ));
                    productsById.put(productId, product);
                });

        return productsById;
    }
}
