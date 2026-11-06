
package com.yasir.miniecommerce.controller;

import com.stripe.exception.SignatureVerificationException;
import com.yasir.miniecommerce.dto.VerifiedStripeEvent;
import com.yasir.miniecommerce.service.StripePaymentService;
import com.yasir.miniecommerce.service.StripeWebhookEventService;
import com.stripe.model.PaymentIntent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StripeWebhookControllerTest {

    @Mock
    private StripePaymentService stripePaymentService;

    @Mock
    private StripeWebhookEventService stripeWebhookEventService;

    @InjectMocks
    private StripeWebhookController stripeWebhookController;

    private static final String RAW_PAYLOAD = "{\"id\":\"evt_test\"}";
    private static final String SIGNATURE = "test_signature";

    private VerifiedStripeEvent verifiedEvent;

    @BeforeEach
    void setUp() {
        verifiedEvent = new VerifiedStripeEvent(
                "evt_controller_test",
                "payment_intent.succeeded",
                mock(PaymentIntent.class)
        );
    }

    @Test
    void shouldProcessVerifiedWebhook() throws Exception {
        when(stripePaymentService.verifyPaymentIntentSucceededWebhook(
                RAW_PAYLOAD, SIGNATURE
        )).thenReturn(Optional.of(verifiedEvent));

        stripeWebhookController.handleWebhook(RAW_PAYLOAD, SIGNATURE);

        verify(stripePaymentService)
                .verifyPaymentIntentSucceededWebhook(RAW_PAYLOAD, SIGNATURE);

        verify(stripeWebhookEventService)
                .processVerifiedEvent(verifiedEvent);
    }

    @Test
    void shouldIgnoreWebhookWhenNoVerifiedEventExists() throws Exception {
        when(stripePaymentService.verifyPaymentIntentSucceededWebhook(
                RAW_PAYLOAD, SIGNATURE
        )).thenReturn(Optional.empty());

        stripeWebhookController.handleWebhook(RAW_PAYLOAD, SIGNATURE);

        verify(stripePaymentService)
                .verifyPaymentIntentSucceededWebhook(RAW_PAYLOAD, SIGNATURE);

        verifyNoInteractions(stripeWebhookEventService);
    }

    @Test
    void shouldPropagateSignatureVerificationException() throws Exception {
        SignatureVerificationException exception =
                new SignatureVerificationException("Invalid signature", SIGNATURE);

        when(stripePaymentService.verifyPaymentIntentSucceededWebhook(
                RAW_PAYLOAD, SIGNATURE
        )).thenThrow(exception);

        SignatureVerificationException thrown = assertThrows(
                SignatureVerificationException.class,
                () -> stripeWebhookController.handleWebhook(RAW_PAYLOAD, SIGNATURE)
        );

        org.junit.jupiter.api.Assertions.assertSame(exception, thrown);

        verifyNoInteractions(stripeWebhookEventService);
    }

    @Test
    void shouldPassNullSignatureToVerificationService() throws Exception {
        when(stripePaymentService.verifyPaymentIntentSucceededWebhook(
                RAW_PAYLOAD, null
        )).thenReturn(Optional.empty());

        stripeWebhookController.handleWebhook(RAW_PAYLOAD, null);

        verify(stripePaymentService)
                .verifyPaymentIntentSucceededWebhook(RAW_PAYLOAD, null);

        verifyNoInteractions(stripeWebhookEventService);
    }
}
