package com.yasir.miniecommerce.repository;

import com.yasir.miniecommerce.model.Product;
import org.springframework.data.jpa.repository.JpaRepository;
    // Burda hazır kütüphanein özelliklerini kullanabiliyoruz . "extends" Sayesinde . Ayrıca "Long" tipinde ID var diye bildiriyoruz .
public interface ProductRepository extends JpaRepository<Product, Long> {
}
