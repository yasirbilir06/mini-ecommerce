package com.yasir.miniecommerce.exception;


import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

// Spring, Controller'larda oluşan hataları bu class'ta yönetebilir.
@RestControllerAdvice
public class GlobalExceptionHandler {
        @ExceptionHandler(MethodArgumentNotValidException.class)
        public ResponseEntity<?> handleValidationException(
                MethodArgumentNotValidException exception) {
    // Hataları alan → mesaj şeklinde saklayacağım.
            Map<String, String> errors = new HashMap<>();
            // Bütün validation hatalarını tek tek dolaş.
            for (FieldError error : exception.getBindingResult().getFieldErrors()) {
                errors.put(error.getField(), error.getDefaultMessage());
            }

            return ResponseEntity.badRequest().body(errors);
        }

    @ExceptionHandler(ProductNotFoundException.class)
    public ResponseEntity<?> handleProductNotFoundException(
            ProductNotFoundException exception) {

        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(exception.getMessage());
    }

}
