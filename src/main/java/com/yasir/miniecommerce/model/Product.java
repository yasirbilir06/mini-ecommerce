package com.yasir.miniecommerce.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;

// Bu java sınıfı veri tabanındaki bir tabloyu temsil ediyor .
@Entity
// Bu Product class'ı MySQL'deki products tablosuna karşılık geliyor.
@Table(name = "products")
public class Product {
        //Bu benim Primary Key'im.
    @Id
        //ID'yi otomatik oluştur. ID'yi veritabanı oluştursun.
    @GeneratedValue(strategy = GenerationType.IDENTITY)
        //ID'nin Java tarafındaki değişkeni.
    private Long id;
    //name" alanı boş veya sadece boşluklardan oluşuyorsa kabul etme.
    @NotBlank(message = "Ürün adı boş bırakılamaz")
    private String name;
    @Positive(message = "Fiyat 0'dan büyük olmalıdır")
    private BigDecimal price;
    @PositiveOrZero(message = "Stok negatif olamaz")
    private Integer stock;

    public Product() {}
    public Product(String name, BigDecimal price, Integer stock) {
        this.name = name;
        this.price = price;
        this.stock = stock;
    }

    public Long getId() {
        return id;
    }


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
