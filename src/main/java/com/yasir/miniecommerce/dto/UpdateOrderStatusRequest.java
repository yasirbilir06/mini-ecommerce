package com.yasir.miniecommerce.dto;

import com.yasir.miniecommerce.model.OrderStatus;
import jakarta.validation.constraints.NotNull;

public class UpdateOrderStatusRequest {

    @NotNull(message = "Sipariş durumu boş bırakılamaz")
    private OrderStatus status;

    public OrderStatus getStatus() {
        return status;
    }

    public void setStatus(OrderStatus status) {
        this.status = status;
    }
}