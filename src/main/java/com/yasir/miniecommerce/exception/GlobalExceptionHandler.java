
package com.yasir.miniecommerce.exception;

import com.stripe.exception.ApiKeyMissingException;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.exception.StripeException;
import com.yasir.miniecommerce.dto.ErrorResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

// Controller katmanındaki hataları merkezi olarak yönetir.
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log =
            LoggerFactory.getLogger(GlobalExceptionHandler.class);

    // DTO validation hataları: 400 Bad Request
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<?> handleValidationException(
            MethodArgumentNotValidException exception) {

        Map<String, String> errors = new HashMap<>();

        for (FieldError error :
                exception.getBindingResult().getFieldErrors()) {
            errors.put(error.getField(), error.getDefaultMessage());
        }

        ErrorResponse response = new ErrorResponse(
                HttpStatus.BAD_REQUEST.value(),
                "Gönderilen veriler geçersiz."
        );

        return ResponseEntity.badRequest().body(
                Map.of(
                        "status", response.getStatus(),
                        "message", response.getMessage(),
                        "timestamp", response.getTimestamp(),
                        "errors", errors
                )
        );
    }

    // Ürün bulunamadı: 404
    @ExceptionHandler(ProductNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleProductNotFoundException(
            ProductNotFoundException exception) {

        return buildErrorResponse(
                HttpStatus.NOT_FOUND,
                exception.getMessage()
        );
    }

    // Kullanıcı bulunamadı: 404
    @ExceptionHandler(UserNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleUserNotFoundException(
            UserNotFoundException exception) {

        return buildErrorResponse(
                HttpStatus.NOT_FOUND,
                exception.getMessage()
        );
    }

    // Sipariş bulunamadı: 404
    @ExceptionHandler(OrderNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleOrderNotFoundException(
            OrderNotFoundException exception) {

        return buildErrorResponse(
                HttpStatus.NOT_FOUND,
                exception.getMessage()
        );
    }

    // Yetersiz stok: 409
    @ExceptionHandler(InsufficientStockException.class)
    public ResponseEntity<ErrorResponse> handleInsufficientStockException(
            InsufficientStockException exception) {

        return buildErrorResponse(
                HttpStatus.CONFLICT,
                exception.getMessage()
        );
    }

    // Geçersiz sipariş durumu: 409
    @ExceptionHandler(InvalidOrderStatusException.class)
    public ResponseEntity<ErrorResponse> handleInvalidOrderStatusException(
            InvalidOrderStatusException exception) {

        return buildErrorResponse(
                HttpStatus.CONFLICT,
                exception.getMessage()
        );
    }

    // Stok veya optimistic locking çakışması: 409
    @ExceptionHandler({
            StockConflictException.class,
            ObjectOptimisticLockingFailureException.class
    })
    public ResponseEntity<ErrorResponse> handleStockConflictException(
            Exception exception) {

        return buildErrorResponse(
                HttpStatus.CONFLICT,
                "Veri başka bir işlem tarafından güncellendi. Lütfen tekrar deneyin."
        );
    }

    // Geçersiz parametreler: 400
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgumentException(
            IllegalArgumentException exception) {

        log.error("IllegalArgumentException occurred:", exception);

        return buildErrorResponse(
                HttpStatus.BAD_REQUEST,
                exception.getMessage()
        );
    }

    // Ortak hata yanıtı oluşturur.
    private ResponseEntity<ErrorResponse> buildErrorResponse(
            HttpStatus status,
            String message) {

        ErrorResponse response = new ErrorResponse(
                status.value(),
                message
        );

        return ResponseEntity.status(status).body(response);
    }

    // Geçersiz giriş bilgileri: 401
    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<ErrorResponse> handleInvalidCredentialsException(
            InvalidCredentialsException exception) {

        return buildErrorResponse(
                HttpStatus.UNAUTHORIZED,
                exception.getMessage()
        );
    }

    // Veritabanı veri çakışması: 409
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrityViolation(
            DataIntegrityViolationException exception) {

        return buildErrorResponse(
                HttpStatus.CONFLICT,
                "İşlem mevcut verilerle çakışıyor."
        );
    }

    // Duplicate email: 409
    @ExceptionHandler(DuplicateEmailException.class)
    public ResponseEntity<ErrorResponse> handleDuplicateEmail(
            DuplicateEmailException exception) {

        return buildErrorResponse(
                HttpStatus.CONFLICT,
                exception.getMessage()
        );
    }

    // Geçersiz idempotency key: 400
    @ExceptionHandler(InvalidIdempotencyKeyException.class)
    public ResponseEntity<ErrorResponse> handleInvalidIdempotencyKey(
            InvalidIdempotencyKeyException exception) {

        return buildErrorResponse(
                HttpStatus.BAD_REQUEST,
                exception.getMessage()
        );
    }

    // Idempotency key çakışması: 409
    @ExceptionHandler(IdempotencyKeyConflictException.class)
    public ResponseEntity<ErrorResponse> handleIdempotencyKeyConflict(
            IdempotencyKeyConflictException exception) {

        return buildErrorResponse(
                HttpStatus.CONFLICT,
                exception.getMessage()
        );
    }

    // Ödeme durumu çakışması: 409
    @ExceptionHandler(PaymentStateConflictException.class)
    public ResponseEntity<ErrorResponse> handlePaymentStateConflict(
            PaymentStateConflictException exception) {

        return buildErrorResponse(
                HttpStatus.CONFLICT,
                exception.getMessage()
        );
    }

    // Stripe API hataları: 502
    @ExceptionHandler(StripeException.class)
    public ResponseEntity<ErrorResponse> handleStripeException(
            StripeException exception) {

        log.error("Stripe API exception occurred:", exception);

        return buildErrorResponse(
                HttpStatus.BAD_GATEWAY,
                "Ödeme sağlayıcısına şu anda ulaşılamıyor. Lütfen daha sonra tekrar deneyin."
        );
    }

    // Stripe webhook imza doğrulama hatası: 400
    @ExceptionHandler(SignatureVerificationException.class)
    public ResponseEntity<ErrorResponse> handleSignatureVerificationException(
            SignatureVerificationException exception) {

        log.error("Stripe webhook signature verification failed:", exception);

        return buildErrorResponse(
                HttpStatus.BAD_REQUEST,
                "Webhook imzası doğrulanamadı."
        );
    }

    // Stripe API key eksik: 502
    @ExceptionHandler(ApiKeyMissingException.class)
    public ResponseEntity<ErrorResponse> handleApiKeyMissingException(
            ApiKeyMissingException exception) {

        log.error("Stripe API key is missing:", exception);

        return buildErrorResponse(
                HttpStatus.BAD_GATEWAY,
                "Ödeme sağlayıcısı yapılandırması şu anda kullanılamıyor."
        );
    }
}
