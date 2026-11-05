
package com.yasir.miniecommerce.dto;

import com.stripe.model.PaymentIntent;

public record VerifiedStripeEvent(
        String eventId,
        String eventType,
        PaymentIntent paymentIntent
) {
}

