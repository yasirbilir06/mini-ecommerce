
package com.yasir.miniecommerce.service;

import com.yasir.miniecommerce.dto.VerifiedStripeEvent;
import com.yasir.miniecommerce.exception.PaymentStateConflictException;
import com.yasir.miniecommerce.model.StripeWebhookEvent;
import com.yasir.miniecommerce.model.StripeWebhookEventStatus;
import com.yasir.miniecommerce.repository.StripeWebhookEventRepository;
import com.stripe.model.PaymentIntent;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.dao.DataIntegrityViolationException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Stripe Webhook Event Service Tests")
class StripeWebhookEventServiceTest {

    private static final String EVENT_ID = "evt_service_test";
    private static final String EVENT_TYPE = "payment_intent.succeeded";
    private static final String PROCESSING_TOKEN = "processing-token";

    @Mock
    private StripeWebhookEventRepository stripeWebhookEventRepository;

    @Mock
    private StripeWebhookEventTransaction stripeWebhookEventTransaction;

    @Mock
    private OrderService orderService;

    @Mock
    private PaymentIntent paymentIntent;

    @InjectMocks
    private StripeWebhookEventService stripeWebhookEventService;

    private VerifiedStripeEvent verifiedEvent;

    @BeforeEach
    void setUp() {
        verifiedEvent = new VerifiedStripeEvent(
                EVENT_ID,
                EVENT_TYPE,
                paymentIntent
        );
    }

    private StripeWebhookEvent createEvent() {
        return new StripeWebhookEvent(EVENT_ID, EVENT_TYPE);
    }

    private void mockNewEventRegistration() {
        when(stripeWebhookEventRepository.findByEventId(EVENT_ID))
                .thenReturn(Optional.empty(), Optional.of(createEvent()));

        when(stripeWebhookEventTransaction.registerEvent(
                EVENT_ID,
                EVENT_TYPE
        )).thenReturn(createEvent());
    }

    private void mockProcessing() {
        when(stripeWebhookEventTransaction.tryMarkAsProcessing(EVENT_ID))
                .thenReturn(Optional.of(PROCESSING_TOKEN));
    }

    @Nested
    @DisplayName("Successful Processing")
    class SuccessfulProcessingTests {

        @Test
        @DisplayName("Should register and process a new event")
        void shouldRegisterAndProcessNewEvent() {
            mockNewEventRegistration();
            mockProcessing();

            when(stripeWebhookEventTransaction.markEventAsProcessed(
                    EVENT_ID,
                    PROCESSING_TOKEN
            )).thenReturn(true);

            assertDoesNotThrow(
                    () -> stripeWebhookEventService.processVerifiedEvent(
                            verifiedEvent
                    )
            );

            verify(stripeWebhookEventTransaction)
                    .registerEvent(EVENT_ID, EVENT_TYPE);

            verify(orderService)
                    .handlePaymentIntentSucceeded(paymentIntent);

            verify(stripeWebhookEventTransaction)
                    .markEventAsProcessed(EVENT_ID, PROCESSING_TOKEN);

            verify(stripeWebhookEventTransaction, never())
                    .markEventAsFailed(anyString(), anyString(), anyString());
        }

        @Test
        @DisplayName("Should process an already registered event")
        void shouldProcessExistingEvent() {
            when(stripeWebhookEventRepository.findByEventId(EVENT_ID))
                    .thenReturn(Optional.of(createEvent()));

            mockProcessing();

            when(stripeWebhookEventTransaction.markEventAsProcessed(
                    EVENT_ID,
                    PROCESSING_TOKEN
            )).thenReturn(true);

            stripeWebhookEventService.processVerifiedEvent(verifiedEvent);

            verify(stripeWebhookEventTransaction, never())
                    .registerEvent(anyString(), anyString());

            verify(orderService)
                    .handlePaymentIntentSucceeded(paymentIntent);
        }

        @Test
        @DisplayName("Should not process event again when already PROCESSED")
        void shouldIgnoreAlreadyProcessedEvent() {
            when(stripeWebhookEventRepository.findByEventId(EVENT_ID))
                    .thenReturn(Optional.of(createEvent()));

            when(stripeWebhookEventTransaction.tryMarkAsProcessing(EVENT_ID))
                    .thenReturn(Optional.empty());

            when(stripeWebhookEventTransaction.findEventStatus(EVENT_ID))
                    .thenReturn(Optional.of(StripeWebhookEventStatus.PROCESSED));

            assertDoesNotThrow(
                    () -> stripeWebhookEventService.processVerifiedEvent(
                            verifiedEvent
                    )
            );

            verifyNoInteractions(orderService);

            verify(stripeWebhookEventTransaction, never())
                    .markEventAsProcessed(anyString(), anyString());
        }

        @Test
        @DisplayName("Should not process event again when REQUIRES_REVIEW")
        void shouldIgnoreEventRequiringReview() {
            when(stripeWebhookEventRepository.findByEventId(EVENT_ID))
                    .thenReturn(Optional.of(createEvent()));

            when(stripeWebhookEventTransaction.tryMarkAsProcessing(EVENT_ID))
                    .thenReturn(Optional.empty());

            when(stripeWebhookEventTransaction.findEventStatus(EVENT_ID))
                    .thenReturn(Optional.of(
                            StripeWebhookEventStatus.REQUIRES_REVIEW
                    ));

            assertDoesNotThrow(
                    () -> stripeWebhookEventService.processVerifiedEvent(
                            verifiedEvent
                    )
            );

            verifyNoInteractions(orderService);
        }
    }

    @Nested
    @DisplayName("Duplicate and Concurrent Delivery")
    class DuplicateDeliveryTests {

        @Test
        @DisplayName("Should throw when event is already PROCESSING")
        void shouldRejectConcurrentProcessing() {
            when(stripeWebhookEventRepository.findByEventId(EVENT_ID))
                    .thenReturn(Optional.of(createEvent()));

            when(stripeWebhookEventTransaction.tryMarkAsProcessing(EVENT_ID))
                    .thenReturn(Optional.empty());

            when(stripeWebhookEventTransaction.findEventStatus(EVENT_ID))
                    .thenReturn(Optional.of(StripeWebhookEventStatus.PROCESSING));

            assertThrows(
                    IllegalStateException.class,
                    () -> stripeWebhookEventService.processVerifiedEvent(
                            verifiedEvent
                    )
            );

            verifyNoInteractions(orderService);
        }

        @Test
        @DisplayName("Should throw when event cannot be found after registration")
        void shouldThrowWhenEventCannotBeFound() {
            when(stripeWebhookEventRepository.findByEventId(EVENT_ID))
                    .thenReturn(Optional.empty(), Optional.empty());

            when(stripeWebhookEventTransaction.registerEvent(
                    EVENT_ID,
                    EVENT_TYPE
            )).thenReturn(createEvent());

            assertThrows(
                    IllegalStateException.class,
                    () -> stripeWebhookEventService.processVerifiedEvent(
                            verifiedEvent
                    )
            );

            verifyNoInteractions(orderService);
        }

        @Test
        @DisplayName("Should handle duplicate registration race")
        void shouldHandleDuplicateRegistrationRace() {
            when(stripeWebhookEventRepository.findByEventId(EVENT_ID))
                    .thenReturn(Optional.empty(), Optional.of(createEvent()));

            when(stripeWebhookEventTransaction.registerEvent(
                    EVENT_ID,
                    EVENT_TYPE
            )).thenThrow(new DataIntegrityViolationException(
                    "Duplicate event ID"
            ));

            mockProcessing();

            when(stripeWebhookEventTransaction.markEventAsProcessed(
                    EVENT_ID,
                    PROCESSING_TOKEN
            )).thenReturn(true);

            assertDoesNotThrow(
                    () -> stripeWebhookEventService.processVerifiedEvent(
                            verifiedEvent
                    )
            );

            verify(orderService)
                    .handlePaymentIntentSucceeded(paymentIntent);

            verify(stripeWebhookEventTransaction)
                    .markEventAsProcessed(EVENT_ID, PROCESSING_TOKEN);
        }
    }

    @Nested
    @DisplayName("Payment State Conflicts")
    class PaymentConflictTests {

        @Test
        @DisplayName("Should move conflicting payment to REQUIRES_REVIEW")
        void shouldMarkConflictAsRequiresReview() {
            mockNewEventRegistration();
            mockProcessing();

            PaymentStateConflictException conflict =
                    new PaymentStateConflictException(
                            "Payment state conflict"
                    );

            doThrow(conflict)
                    .when(orderService)
                    .handlePaymentIntentSucceeded(paymentIntent);

            when(stripeWebhookEventTransaction.markEventAsRequiresReview(
                    EVENT_ID,
                    PROCESSING_TOKEN,
                    conflict.getMessage()
            )).thenReturn(true);

            assertDoesNotThrow(
                    () -> stripeWebhookEventService.processVerifiedEvent(
                            verifiedEvent
                    )
            );

            verify(stripeWebhookEventTransaction)
                    .markEventAsRequiresReview(
                            EVENT_ID,
                            PROCESSING_TOKEN,
                            conflict.getMessage()
                    );

            verify(stripeWebhookEventTransaction, never())
                    .markEventAsFailed(anyString(), anyString(), anyString());
        }

        @Test
        @DisplayName("Should throw when REQUIRES_REVIEW update fails")
        void shouldThrowWhenReviewUpdateFails() {
            mockNewEventRegistration();
            mockProcessing();

            PaymentStateConflictException conflict =
                    new PaymentStateConflictException("Payment conflict");

            doThrow(conflict)
                    .when(orderService)
                    .handlePaymentIntentSucceeded(paymentIntent);

            when(stripeWebhookEventTransaction.markEventAsRequiresReview(
                    EVENT_ID,
                    PROCESSING_TOKEN,
                    conflict.getMessage()
            )).thenReturn(false);

            assertThrows(
                    IllegalStateException.class,
                    () -> stripeWebhookEventService.processVerifiedEvent(
                            verifiedEvent
                    )
            );
        }

        @Test
        @DisplayName("Should throw when REQUIRES_REVIEW persistence fails")
        void shouldThrowWhenReviewPersistenceThrows() {
            mockNewEventRegistration();
            mockProcessing();

            PaymentStateConflictException conflict =
                    new PaymentStateConflictException("Payment conflict");

            doThrow(conflict)
                    .when(orderService)
                    .handlePaymentIntentSucceeded(paymentIntent);

            when(stripeWebhookEventTransaction.markEventAsRequiresReview(
                    EVENT_ID,
                    PROCESSING_TOKEN,
                    conflict.getMessage()
            )).thenThrow(new RuntimeException("Database unavailable"));

            assertThrows(
                    IllegalStateException.class,
                    () -> stripeWebhookEventService.processVerifiedEvent(
                            verifiedEvent
                    )
            );
        }
    }

    @Nested
    @DisplayName("Failed Processing")
    class FailedProcessingTests {

        @Test
        @DisplayName("Should mark event as FAILED when order processing fails")
        void shouldMarkEventAsFailed() {
            mockNewEventRegistration();
            mockProcessing();

            RuntimeException processingException =
                    new RuntimeException("Unexpected payment error");

            doThrow(processingException)
                    .when(orderService)
                    .handlePaymentIntentSucceeded(paymentIntent);

            when(stripeWebhookEventTransaction.markEventAsFailed(
                    EVENT_ID,
                    PROCESSING_TOKEN,
                    processingException.getMessage()
            )).thenReturn(true);

            RuntimeException thrown = assertThrows(
                    RuntimeException.class,
                    () -> stripeWebhookEventService.processVerifiedEvent(
                            verifiedEvent
                    )
            );

            assertSame(processingException, thrown);

            verify(stripeWebhookEventTransaction)
                    .markEventAsFailed(
                            EVENT_ID,
                            PROCESSING_TOKEN,
                            processingException.getMessage()
                    );
        }

        @Test
        @DisplayName("Should rethrow original exception when FAILED update fails")
        void shouldRethrowOriginalExceptionWhenFailureUpdateFails() {
            mockNewEventRegistration();
            mockProcessing();

            RuntimeException originalException =
                    new RuntimeException("Payment service unavailable");

            doThrow(originalException)
                    .when(orderService)
                    .handlePaymentIntentSucceeded(paymentIntent);

            when(stripeWebhookEventTransaction.markEventAsFailed(
                    EVENT_ID,
                    PROCESSING_TOKEN,
                    originalException.getMessage()
            )).thenThrow(new RuntimeException("Database unavailable"));

            RuntimeException thrown = assertThrows(
                    RuntimeException.class,
                    () -> stripeWebhookEventService.processVerifiedEvent(
                            verifiedEvent
                    )
            );

            assertSame(originalException, thrown);
        }

        @Test
        @DisplayName("Should rethrow when FAILED status update returns false")
        void shouldRethrowWhenFailedUpdateReturnsFalse() {
            mockNewEventRegistration();
            mockProcessing();

            RuntimeException originalException =
                    new RuntimeException("Payment processing error");

            doThrow(originalException)
                    .when(orderService)
                    .handlePaymentIntentSucceeded(paymentIntent);

            when(stripeWebhookEventTransaction.markEventAsFailed(
                    EVENT_ID,
                    PROCESSING_TOKEN,
                    originalException.getMessage()
            )).thenReturn(false);

            RuntimeException thrown = assertThrows(
                    RuntimeException.class,
                    () -> stripeWebhookEventService.processVerifiedEvent(
                            verifiedEvent
                    )
            );

            assertSame(originalException, thrown);
        }
    }

    @Nested
    @DisplayName("Processed State Persistence")
    class ProcessedPersistenceTests {

        @Test
        @DisplayName("Should throw when PROCESSED update returns false")
        void shouldThrowWhenProcessedUpdateReturnsFalse() {
            mockNewEventRegistration();
            mockProcessing();

            when(stripeWebhookEventTransaction.markEventAsProcessed(
                    EVENT_ID,
                    PROCESSING_TOKEN
            )).thenReturn(false);

            assertThrows(
                    IllegalStateException.class,
                    () -> stripeWebhookEventService.processVerifiedEvent(
                            verifiedEvent
                    )
            );

            verify(stripeWebhookEventTransaction)
                    .markEventAsFailed(
                            EVENT_ID,
                            PROCESSING_TOKEN,
                            "Webhook event PROCESSED yapılamadı: " + EVENT_ID
                    );
        }
    }
}
