package com.yasir.miniecommerce.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public class ProductRequest {

    @NotBlank(message = "Ürün adı boş bırakılamaz")
    private String name;
    @NotNull(message = "Fiyat boş bırakılamaz")
    @Positive(message = "Fiyat 0'dan büyük olmalıdır")
    private BigDecimal price;
    @NotNull(message = "Fiyat   boş bırakılamaz")
    @PositiveOrZero(message = "Stok negatif olamaz")
    private Integer stock;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public Integer getStock() {
        return stock;
    }

    public void setStock(Integer stock) {
        this.stock = stock;
    }
}