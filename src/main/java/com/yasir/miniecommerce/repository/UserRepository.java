package com.yasir.miniecommerce.repository;

import com.yasir.miniecommerce.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    // Spring Data JPA burada method adına bakıp gerekli SQL sorgusunu kendisi oluşturuyor.
    Optional<User> findByEmail(String email);
}