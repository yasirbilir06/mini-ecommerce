package com.yasir.miniecommerce.repository;

import com.yasir.miniecommerce.model.Order;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long> {

    List<Order> findByUserEmail(String email);

    Optional<Order> findByIdAndUserEmail(Long id, String email);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM Order o WHERE o.id = :id AND o.user.email = :email")
    Optional<Order> findByIdAndUserEmailWithLock(
            // Java metodundaki parametreleri sorgudaki isimlerle eşleştirir.
            @Param("id") Long id,
            @Param("email") String email

    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM Order o WHERE o.id = :id")
    Optional<Order> findByIdWithLock(@Param("id") Long id);
}