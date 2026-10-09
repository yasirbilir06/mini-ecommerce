package com.yasir.miniecommerce.controller;

import com.yasir.miniecommerce.dto.OrderRequest;
import com.yasir.miniecommerce.dto.OrderResponse;
import com.yasir.miniecommerce.dto.UpdateOrderStatusRequest;
import com.yasir.miniecommerce.service.OrderService;

import jakarta.validation.Valid;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    // Sipariş oluştur.
    @PreAuthorize("isAuthenticated()")
    @PostMapping
    public OrderResponse createOrder(
            @Valid @RequestBody OrderRequest orderRequest) {

        return orderService.createOrder(orderRequest);
    }

    // Giriş yapan kullanıcının siparişlerini listele.
    @GetMapping
    public List<OrderResponse> getMyOrders() {
        return orderService.getMyOrders();
    }

    // Kullanıcının kendi siparişini getir.
    @GetMapping("/{id}")
    public OrderResponse getMyOrderById(@PathVariable Long id) {
        return orderService.getMyOrderById(id);
    }

    // Kullanıcının kendi siparişini iptal et.
    @PreAuthorize("isAuthenticated()")
    @PutMapping("/{id}/cancel")
    public OrderResponse cancelMyOrder(@PathVariable Long id) {
        return orderService.cancelMyOrder(id);
    }

    // Yalnızca ADMIN sipariş durumunu değiştirebilir.
    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/{id}/status")
    public OrderResponse updateOrderStatus(
            @PathVariable Long id,
            @Valid @RequestBody UpdateOrderStatusRequest request) {

        return orderService.updateOrderStatus(id, request);
    }
}