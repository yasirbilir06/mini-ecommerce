package com.yasir.miniecommerce.exception;


import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
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

    @ExceptionHandler(InsufficientStockException.class)
    public ResponseEntity<?> handleInsufficientStockException(
            InsufficientStockException exception) {

        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(exception.getMessage());
    }

    @ExceptionHandler(UserNotFoundException.class)
    public ResponseEntity<?> handleUserNotFoundException(
            UserNotFoundException exception) {

        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(exception.getMessage());
    }

    @ExceptionHandler(OrderNotFoundException.class)
    public ResponseEntity<?> handleOrderNotFoundException(
            OrderNotFoundException exception) {

        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(exception.getMessage());
    }

    @ExceptionHandler(InvalidOrderStatusException.class)
    public ResponseEntity<?> handleInvalidOrderStatusException(
            InvalidOrderStatusException exception) {

        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(exception.getMessage());
    }

    @ExceptionHandler({
            StockConflictException.class,
            ObjectOptimisticLockingFailureException.class
    })
    public ResponseEntity<?> handleStockConflictException(
            Exception exception) {

        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body("Ürün stoğu başka bir işlem tarafından güncellendi. Lütfen tekrar deneyin.");
    }

}
