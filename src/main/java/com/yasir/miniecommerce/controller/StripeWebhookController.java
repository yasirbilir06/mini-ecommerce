package com.yasir.miniecommerce.controller;

import com.stripe.exception.SignatureVerificationException;
import com.yasir.miniecommerce.service.OrderService;
import com.yasir.miniecommerce.service.StripePaymentService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/stripe")
public class StripeWebhookController {

    private final StripePaymentService stripePaymentService;
    private final OrderService orderService;

    public StripeWebhookController(
            StripePaymentService stripePaymentService,
            OrderService orderService) {

        this.stripePaymentService = stripePaymentService;
        this.orderService = orderService;
    }

    @PostMapping(value = "/webhook", consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.OK)
    public void handleWebhook(
            @RequestBody String rawPayload,
            @RequestHeader(value = "Stripe-Signature", required = false)
            String signatureHeader) throws SignatureVerificationException {

        stripePaymentService
                .verifyPaymentIntentSucceededWebhook(rawPayload, signatureHeader)
                .ifPresent(orderService::handlePaymentIntentSucceeded);
    }
}
