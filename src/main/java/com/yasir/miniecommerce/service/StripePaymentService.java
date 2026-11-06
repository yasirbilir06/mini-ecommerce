
    package com.yasir.miniecommerce.service;

    import com.stripe.StripeClient;
    import com.stripe.exception.StripeException;
    import com.stripe.exception.SignatureVerificationException;
    import com.stripe.model.Event;
    import com.stripe.model.PaymentIntent;
    import com.stripe.model.StripeObject;
    import com.stripe.net.Webhook;
    import com.stripe.net.RequestOptions;
    import com.stripe.param.PaymentIntentCreateParams;
    import com.yasir.miniecommerce.dto.VerifiedStripeEvent;
    import org.springframework.beans.factory.annotation.Value;
    import org.springframework.stereotype.Service;

    import java.util.Locale;
    import java.util.Optional;

    @Service
    public class StripePaymentService {

        private final StripeClient stripeClient;
        private final String webhookSigningSecret;

        public StripePaymentService(
                StripeClient stripeClient,
                @Value("${stripe.webhook-secret}") String webhookSigningSecret) {

            this.stripeClient = stripeClient;
            if (webhookSigningSecret == null || webhookSigningSecret.isBlank()) {
                throw new IllegalStateException(
                        "Stripe webhook signing secret must be configured."
                );
            }
            this.webhookSigningSecret = webhookSigningSecret;
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

        public PaymentIntent retrievePaymentIntent(String paymentIntentId)
                throws StripeException {

            if (paymentIntentId == null || paymentIntentId.isBlank()) {
                throw new IllegalArgumentException(
                        "PaymentIntent ID cannot be empty."
                );
            }

            return stripeClient.v1()
                    .paymentIntents()
                    .retrieve(paymentIntentId);
        }

        public Optional<VerifiedStripeEvent> verifyPaymentIntentSucceededWebhook(
                String rawPayload,
                String signatureHeader) throws SignatureVerificationException {

            if (rawPayload == null || signatureHeader == null || signatureHeader.isBlank()) {
                throw new SignatureVerificationException(
                        "Webhook signature verification failed.",
                        signatureHeader
                );
            }

            Event event = Webhook.constructEvent(
                    rawPayload,
                    signatureHeader,
                    webhookSigningSecret
            );

            if (!"payment_intent.succeeded".equals(event.getType())) {
                return Optional.empty();
            }

            StripeObject eventObject = event.getDataObjectDeserializer()
                    .getObject()
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Webhook event payload is invalid."
                    ));

            if (!(eventObject instanceof PaymentIntent paymentIntent)) {
                throw new IllegalArgumentException("Webhook event payload is invalid.");
            }

            return Optional.of(
                    new VerifiedStripeEvent(
                            event.getId(),
                            event.getType(),
                            paymentIntent
                    )
            );
        }
    }
