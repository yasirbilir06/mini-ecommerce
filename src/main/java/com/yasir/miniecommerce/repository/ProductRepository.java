package com.yasir.miniecommerce.repository;

import com.yasir.miniecommerce.model.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

// Burda hazır kütüphanein özelliklerini kullanabiliyoruz . "extends" Sayesinde . Ayrıca "Long" tipinde ID var diye bildiriyoruz .
public interface ProductRepository extends JpaRepository<Product, Long> {
        // Veritabanı kilitleme davranışını belirler. Ürün kaydını işlem sırasında yazma kilidiyle korur.
        @Lock(LockModeType.PESSIMISTIC_WRITE)
        @Query("SELECT p FROM Product p WHERE p.id = :id")
        Optional<Product> findByIdWithLock(@Param("id") Long id);
}
