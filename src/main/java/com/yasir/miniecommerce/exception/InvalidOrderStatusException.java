package com.yasir.miniecommerce.exception;

public class InvalidOrderStatusException extends RuntimeException {

    public InvalidOrderStatusException(String message) {
        super(message);
    }
}