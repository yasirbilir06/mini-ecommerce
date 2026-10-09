package com.yasir.miniecommerce.mapper;

import com.yasir.miniecommerce.dto.OrderItemResponse;
import com.yasir.miniecommerce.dto.OrderResponse;
import com.yasir.miniecommerce.model.Order;
import com.yasir.miniecommerce.model.OrderItem;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class OrderMapper {

    public OrderResponse toResponse(Order order) {

        OrderResponse response = new OrderResponse();

        response.setId(order.getId());
        response.setUserEmail(order.getUser().getEmail());
        response.setTotalPrice(order.getTotalPrice());
        response.setOrderStatus(order.getStatus());

        List<OrderItemResponse> items = order.getOrderItems()
                .stream()
                .map(this::toItemResponse)
                .toList();

        response.setItems(items);

        return response;
    }

    private OrderItemResponse toItemResponse(OrderItem orderItem) {

        OrderItemResponse response = new OrderItemResponse();

        response.setId(orderItem.getId());
        response.setProductId(orderItem.getProduct().getId());
        response.setProductName(orderItem.getProduct().getName());
        response.setQuantity(orderItem.getQuantity());
        response.setPrice(orderItem.getPrice());

        return response;
    }
}