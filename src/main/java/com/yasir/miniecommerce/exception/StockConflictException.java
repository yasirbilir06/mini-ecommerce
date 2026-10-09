package com.yasir.miniecommerce.exception;

public class StockConflictException extends RuntimeException {

    public StockConflictException(String message) {
        super(message);
    }
}