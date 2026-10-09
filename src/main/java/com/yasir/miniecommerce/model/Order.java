package com.yasir.miniecommerce.model;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "orders")
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private BigDecimal totalPrice;
    // Enum'u database'de sayı olarak değil, yazı olarak sakla.
    @Enumerated(EnumType.STRING)
    private OrderStatus status;
    // Bir Order'ın birçok OrderItem'ı var ve ben bunları List olarak tutuyorum.
    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL)
    private List<OrderItem> orderItems = new ArrayList<>();

    @ManyToOne
    @JoinColumn(name = "user_id")
    private User user;

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public Order() {

    }

    public Long getId() {
        return id;
    }

    public BigDecimal getTotalPrice() {
        return totalPrice;
    }

    public List<OrderItem> getOrderItems() {
        return orderItems;
    }

    public void setTotalPrice(BigDecimal totalPrice) {
        this.totalPrice = totalPrice;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public void setStatus(OrderStatus status) {
        this.status = status;
    }

    public void setOrderItems(List<OrderItem> orderItems) {
        this.orderItems = orderItems;
    }
}