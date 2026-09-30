package com.yasir.miniecommerce.mapper;

import com.yasir.miniecommerce.dto.ProductResponse;
import com.yasir.miniecommerce.model.Product;
import org.springframework.stereotype.Component;
    //Bu ProductMapper sınıfını sen yönet, gerektiğinde bana ver.
@Component
public class ProductMapper {

    public ProductResponse toResponse(Product product) {

        ProductResponse response = new ProductResponse();

        response.setId(product.getId());
        response.setName(product.getName());
        response.setPrice(product.getPrice());
        response.setStock(product.getStock());

        return response;
    }
}