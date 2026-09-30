package com.yasir.miniecommerce.controller;

import com.yasir.miniecommerce.dto.ProductRequest;
import com.yasir.miniecommerce.dto.ProductResponse;
import com.yasir.miniecommerce.model.Product;
import com.yasir.miniecommerce.service.ProductService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

// Bu class HTTP isteklerini karşılayacak bir REST Controller."
@RestController
public class ProductController {
    private ProductService productService;
        public ProductController(ProductService productService) {
            this.productService = productService;
        }
        // Birisi /api/products adresine GET isteği gönderirse bu metodu çalıştır.
        @GetMapping("/api/products")
        public List<ProductResponse> getAllProducts() {
            return productService.getAllProducts();
        }

    @PostMapping("/api/products")
    public ProductResponse createProduct(
            @Valid @RequestBody ProductRequest productRequest) {
        return productService.createProduct(productRequest);
    }

    @GetMapping("/api/products/{id}")
    public Product getProductById(@PathVariable Long id) {
        return productService.getProductById(id);
    }

    public ProductResponse updateProduct(
            @PathVariable Long id,
            @Valid @RequestBody ProductRequest productRequest) {
        return productService.updateProduct(id, productRequest);
    }
        // Bu URL'ye DELETE isteği gelirse bu metodu çalıştır
    @DeleteMapping("/api/products/{id}")
    public void deleteProduct(@PathVariable Long id) {
        productService.deleteProduct(id);
    }

}
