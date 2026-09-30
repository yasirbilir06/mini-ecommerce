package com.yasir.miniecommerce.service;

import com.yasir.miniecommerce.dto.ProductRequest;
import com.yasir.miniecommerce.dto.ProductResponse;
import com.yasir.miniecommerce.exception.ProductNotFoundException;
import com.yasir.miniecommerce.mapper.ProductMapper;
import com.yasir.miniecommerce.model.Product;
import com.yasir.miniecommerce.repository.ProductRepository;
import org.springframework.stereotype.Service;

import java.util.List;

// Bu class benim servis katmanım, bunu sen yönet dedik.
@Service
public class ProductService {

    private ProductRepository productRepository;
    private ProductMapper productMapper;


    // Product → ProductResponse dönüşümü
    public ProductService(
            ProductRepository productRepository,
            ProductMapper productMapper) {

        this.productRepository = productRepository;
        this.productMapper = productMapper;
    }

    // CREATE
    public ProductResponse createProduct(ProductRequest productRequest) {

        Product product = new Product();

        product.setName(productRequest.getName());
        product.setPrice(productRequest.getPrice());
        product.setStock(productRequest.getStock());

        Product savedProduct = productRepository.save(product);

        return productMapper.toResponse(savedProduct);
    }

    // READ - TÜM ÜRÜNLER
    public List<ProductResponse> getAllProducts() {

        return productRepository.findAll()
                .stream()
                .map(productMapper::toResponse)
                .toList();
    }

    // READ - TEK ÜRÜN
    public Product getProductById(Long id) {

        return productRepository.findById(id)
                .orElseThrow(() ->
                        new ProductNotFoundException("Ürün bulunamadı: " + id));
    }

    // UPDATE
    public ProductResponse updateProduct(Long id, ProductRequest productRequest) {

        Product existingProduct = productRepository.findById(id)
                .orElseThrow(() ->
                        new ProductNotFoundException("Ürün bulunamadı: " + id));

        existingProduct.setName(productRequest.getName());
        existingProduct.setPrice(productRequest.getPrice());
        existingProduct.setStock(productRequest.getStock());

        Product updatedProduct = productRepository.save(existingProduct);

        return productMapper.toResponse(updatedProduct);
    }

    // DELETE
    // Void bize ürün döndürmüyor.
    public void deleteProduct(Long id) {

        Product product = productRepository.findById(id)
                .orElseThrow(() ->
                        new ProductNotFoundException("Ürün bulunamadı: " + id));

        productRepository.delete(product);
    }
}