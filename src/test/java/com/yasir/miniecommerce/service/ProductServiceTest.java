package com.yasir.miniecommerce.service;

import com.yasir.miniecommerce.dto.ProductRequest;
import com.yasir.miniecommerce.dto.ProductResponse;
import com.yasir.miniecommerce.exception.ProductNotFoundException;
import com.yasir.miniecommerce.mapper.ProductMapper;
import com.yasir.miniecommerce.model.Product;
import com.yasir.miniecommerce.repository.ProductRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// JUnit, bu test sınıfında Mockito annotation'larını da çalıştır.
@ExtendWith(MockitoExtension.class)


public class ProductServiceTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private ProductMapper productMapper;

    @InjectMocks
    private ProductService productService;

    @Test
    void testCreateProduct() {

        ProductRequest request = new ProductRequest();
        request.setName("iPhone 18");
        request.setPrice(new BigDecimal("1200"));
        request.setStock(10);

        Product product = new Product();
        product.setName("iPhone 18");
        product.setPrice(new BigDecimal("1200"));
        product.setStock(10);

        when(productRepository.save(any(Product.class)))
                .thenReturn(product);

        ProductResponse response = new ProductResponse();
        response.setName("iPhone 18");
        response.setPrice(new BigDecimal("1200"));
        response.setStock(10);

        when(productMapper.toResponse(product))
                .thenReturn(response);
        ProductResponse result = productService.createProduct(request);
        assertEquals("iPhone 18", result.getName());
        assertEquals(new BigDecimal("1200"), result.getPrice());
        assertEquals(10, result.getStock());

        verify(productRepository).save(any(Product.class));
    }
    @Test
    void testGetProductByIdNotFound() {
        when(productRepository.findById(999L))
                .thenReturn(Optional.empty());
        assertThrows(ProductNotFoundException.class, () -> {
            productService.getProductById(999L);
        });


    }
}