package com.yasir.miniecommerce.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public class OrderItemRequest {

    @NotNull(message = "Ürün ID boş bırakılamaz")
    private Long productId;

    @NotNull(message = "Miktar boş bırakılamaz")
    @Positive(message = "Miktar 0'dan büyük olmalıdır")
    private Integer quantity;

    public Long getProductId() {
        return productId;
    }

    public void setProductId(Long productId) {
        this.productId = productId;
    }

    public Integer getQuantity() {
        return quantity;
    }

    public void setQuantity(Integer quantity) {
        this.quantity = quantity;
    }
}