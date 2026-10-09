package com.yasir.miniecommerce.repository;

import com.yasir.miniecommerce.model.Order;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long> {

    List<Order> findByUserEmail(String email);

    Optional<Order> findByIdAndUserEmail(Long id, String email);
}
