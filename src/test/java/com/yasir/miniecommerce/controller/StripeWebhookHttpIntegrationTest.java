
package com.yasir.miniecommerce.controller;

import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.PaymentIntent;
import com.yasir.miniecommerce.dto.VerifiedStripeEvent;
import com.yasir.miniecommerce.service.StripePaymentService;
import com.yasir.miniecommerce.service.StripeWebhookEventService;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("mysql-test")
class StripeWebhookHttpIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private StripePaymentService stripePaymentService;

    @MockitoBean
    private StripeWebhookEventService stripeWebhookEventService;

    private static final String PAYLOAD = "{\"id\":\"evt_http_test\"}";
    private static final String SIGNATURE = "test_signature";

    @Test
    void shouldReturn200WhenWebhookIsValid() throws Exception {

        VerifiedStripeEvent event = new VerifiedStripeEvent(
                "evt_http_test",
                "payment_intent.succeeded",
                mock(PaymentIntent.class)
        );

        when(stripePaymentService.verifyPaymentIntentSucceededWebhook(
                PAYLOAD,
                SIGNATURE
        )).thenReturn(Optional.of(event));

        mockMvc.perform(post("/api/stripe/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Stripe-Signature", SIGNATURE)
                        .content(PAYLOAD))
                .andExpect(status().isOk());

        verify(stripePaymentService)
                .verifyPaymentIntentSucceededWebhook(PAYLOAD, SIGNATURE);

        verify(stripeWebhookEventService)
                .processVerifiedEvent(event);
    }

    @Test
    void shouldReturn200WhenNoRelevantEventExists() throws Exception {

        when(stripePaymentService.verifyPaymentIntentSucceededWebhook(
                PAYLOAD,
                SIGNATURE
        )).thenReturn(Optional.empty());

        mockMvc.perform(post("/api/stripe/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Stripe-Signature", SIGNATURE)
                        .content(PAYLOAD))
                .andExpect(status().isOk());

        verify(stripePaymentService)
                .verifyPaymentIntentSucceededWebhook(PAYLOAD, SIGNATURE);

        verifyNoInteractions(stripeWebhookEventService);
    }

    @Test
    void shouldReturn400WhenSignatureIsInvalid() throws Exception {

        SignatureVerificationException exception =
                new SignatureVerificationException(
                        "Invalid signature",
                        SIGNATURE
                );

        when(stripePaymentService.verifyPaymentIntentSucceededWebhook(
                PAYLOAD,
                SIGNATURE
        )).thenThrow(exception);

        mockMvc.perform(post("/api/stripe/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Stripe-Signature", SIGNATURE)
                        .content(PAYLOAD))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message")
                        .value("Webhook imzası doğrulanamadı."));

        verifyNoInteractions(stripeWebhookEventService);
    }

    @Test
    void shouldAllowWebhookWithoutJwtAuthentication() throws Exception {

        when(stripePaymentService.verifyPaymentIntentSucceededWebhook(
                PAYLOAD,
                SIGNATURE
        )).thenReturn(Optional.empty());

        mockMvc.perform(post("/api/stripe/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Stripe-Signature", SIGNATURE)
                        .content(PAYLOAD))
                .andExpect(status().isOk());

        verify(stripePaymentService)
                .verifyPaymentIntentSucceededWebhook(PAYLOAD, SIGNATURE);
    }

    @Test
    void shouldReturn401ForProtectedEndpointWithoutJwt() throws Exception {

        mockMvc.perform(get("/api/orders"))
                .andExpect(status().isUnauthorized());
    }
}
