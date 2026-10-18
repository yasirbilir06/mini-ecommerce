package com.yasir.miniecommerce.dto;

import jakarta.validation.Valid;

import java.util.List;
import jakarta.validation.constraints.NotEmpty;

public class OrderRequest {
    @NotEmpty(message = "Sipariş en az bir ürün içermelidir")
    private List<@Valid OrderItemRequest> items;

    public List<OrderItemRequest> getItems() {
        return items;
    }

    public void setItems(List<OrderItemRequest> items) {
        this.items = items;
    }
}