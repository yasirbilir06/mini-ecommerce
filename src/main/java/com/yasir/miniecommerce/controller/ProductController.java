package com.yasir.miniecommerce.controller;

import com.yasir.miniecommerce.dto.ProductRequest;
import com.yasir.miniecommerce.dto.ProductResponse;
import com.yasir.miniecommerce.service.ProductService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;


import java.util.List;

@RestController
@RequestMapping("/api/products")
public class ProductController {

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    // Tüm ürünleri listele
    @GetMapping
    @Operation(summary = "Tüm ürünleri sayfalı ve sıralı getir")
    public Page<ProductResponse> getAllProducts(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "id,asc") String sort
    ) {
        if (page < 0) {
            throw new IllegalArgumentException(
                    "Page 0 veya daha büyük olmalıdır"
            );
        }

        if (size < 1) {
            throw new IllegalArgumentException(
                    "Size en az 1 olmalıdır"
            );
        }
        if (sort == null || sort.isBlank()) {
            throw new IllegalArgumentException(
                    "Sort parametresi boş olamaz"
            );
        }

        String[] sortParts = sort.split(",");

        String property = sortParts[0];
        if (!property.equals("id")
                && !property.equals("name")
                && !property.equals("price")
                && !property.equals("stock")) {
            throw new IllegalArgumentException(
                    "Geçersiz sıralama alanı: " + property
            );
        }

        Sort.Direction direction;

        try {
            direction = sortParts.length > 1
                    ? Sort.Direction.fromString(sortParts[1])
                    : Sort.Direction.ASC;
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    "Sıralama yönü asc veya desc olmalıdır"
            );
        }



        Pageable pageable = PageRequest.of(
                page,
                Math.min(size, 100),
                Sort.by(direction, property)
        );

        return productService.getAllProducts(pageable);
    }

    // ID ile ürün getir
    @GetMapping("/{id}")
    public ProductResponse getProductById(@PathVariable Long id) {
        return productService.getProductById(id);
    }


    // Yeni ürün oluştur (Yalnızca ADMIN)
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping
    @SecurityRequirement(name = "bearerAuth")
    @ResponseStatus(HttpStatus.CREATED)
    public ProductResponse createProduct(
            @Valid @RequestBody ProductRequest productRequest) {

        return productService.createProduct(productRequest);
    }

    // Ürünü güncelle (Yalnızca ADMIN)
    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/{id}")
    @SecurityRequirement(name = "bearerAuth")
    public ProductResponse updateProduct(
            @PathVariable Long id,
            @Valid @RequestBody ProductRequest productRequest) {

        return productService.updateProduct(id, productRequest);
    }

    // Ürünü sil (Yalnızca ADMIN)
    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/{id}")
    @SecurityRequirement(name = "bearerAuth")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteProduct(@PathVariable Long id) {
        productService.deleteProduct(id);
    }
}
