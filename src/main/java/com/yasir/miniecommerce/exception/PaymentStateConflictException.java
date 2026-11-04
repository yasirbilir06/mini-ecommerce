package com.yasir.miniecommerce.exception;

public class PaymentStateConflictException extends RuntimeException {

    public PaymentStateConflictException(String message) {
        super(message);
    }
}
