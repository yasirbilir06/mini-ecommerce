
package com.yasir.miniecommerce.service;

import com.stripe.StripeClient;
import com.stripe.exception.StripeException;
import com.stripe.model.PaymentIntent;
import com.stripe.net.RequestOptions;
import com.stripe.param.PaymentIntentCreateParams;
import org.springframework.stereotype.Service;

import java.util.Locale;

@Service
public class StripePaymentService {

    private final StripeClient stripeClient;

    public StripePaymentService(StripeClient stripeClient) {
        this.stripeClient = stripeClient;
    }

    public PaymentIntent createPaymentIntent(
            long amountInCents,
            String currency,
            String orderId,
            String idempotencyKey
    ) throws StripeException {

        // Validate input parameters
        if (amountInCents <= 0) {
            throw new IllegalArgumentException(
                    "Payment amount must be greater than zero."
            );
        }

        if (currency == null || currency.isBlank()) {
            throw new IllegalArgumentException(
                    "Currency cannot be empty."
            );
        }

        if (orderId == null || orderId.isBlank()) {
            throw new IllegalArgumentException(
                    "Order ID cannot be empty."
            );
        }

        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException(
                    "Idempotency key cannot be empty."
            );
        }

        // Build Stripe PaymentIntent parameters
        PaymentIntentCreateParams params =
                PaymentIntentCreateParams.builder()
                        .setAmount(amountInCents)
                        .setCurrency(currency.toLowerCase(Locale.ROOT))
                        .setDescription("ShopAPI Order " + orderId)
                        .putMetadata("orderId", orderId)
                        .build();

        // Configure idempotency
        RequestOptions requestOptions =
                RequestOptions.builder()
                        .setIdempotencyKey(idempotencyKey)
                        .build();

        // Create PaymentIntent through Stripe API
        return stripeClient.v1()
                .paymentIntents()
                .create(params, requestOptions);
    }
}
