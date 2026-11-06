
package com.yasir.miniecommerce.service;

import com.yasir.miniecommerce.model.StripeWebhookEvent;
import com.yasir.miniecommerce.model.StripeWebhookEventStatus;
import com.yasir.miniecommerce.repository.StripeWebhookEventRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("mysql-test")
@DisplayName("Stripe Webhook Event Transaction Integration Tests")
class StripeWebhookEventTransactionTest {

    private static final String EVENT_ID = "evt_transaction_test";
    private static final String EVENT_TYPE = "payment_intent.succeeded";

    @Autowired
    private StripeWebhookEventTransaction stripeWebhookEventTransaction;

    @Autowired
    private StripeWebhookEventRepository stripeWebhookEventRepository;

    @BeforeEach
    void setUp() {
        stripeWebhookEventRepository.deleteAll();
    }

    private StripeWebhookEvent createEvent(String eventId) {
        return stripeWebhookEventTransaction.registerEvent(
                eventId,
                EVENT_TYPE
        );
    }

    private String startProcessing(String eventId) {
        Optional<String> token =
                stripeWebhookEventTransaction.tryMarkAsProcessing(eventId);

        assertTrue(token.isPresent(), "Processing token should be generated");

        return token.orElseThrow();
    }

    @Nested
    @DisplayName("Event Registration")
    class EventRegistrationTests {

        @Test
        @DisplayName("Should register a new webhook event")
        void shouldRegisterEvent() {
            StripeWebhookEvent event = createEvent(EVENT_ID);

            assertAll(
                    () -> assertNotNull(event.getId()),
                    () -> assertEquals(EVENT_ID, event.getEventId()),
                    () -> assertEquals(EVENT_TYPE, event.getEventType()),
                    () -> assertEquals(
                            StripeWebhookEventStatus.RECEIVED,
                            event.getStatus()
                    )
            );

            assertTrue(
                    stripeWebhookEventRepository.findByEventId(EVENT_ID).isPresent()
            );
        }

        @Test
        @DisplayName("Should reject duplicate event ID")
        void shouldRejectDuplicateEventId() {
            createEvent(EVENT_ID);

            assertThrows(
                    DataIntegrityViolationException.class,
                    () -> createEvent(EVENT_ID)
            );
        }

        @Test
        @DisplayName("Should return event status")
        void shouldFindEventStatus() {
            createEvent(EVENT_ID);

            Optional<StripeWebhookEventStatus> status =
                    stripeWebhookEventTransaction.findEventStatus(EVENT_ID);

            assertTrue(status.isPresent());
            assertEquals(
                    StripeWebhookEventStatus.RECEIVED,
                    status.orElseThrow()
            );
        }

        @Test
        @DisplayName("Should return empty status for unknown event")
        void shouldReturnEmptyStatusForUnknownEvent() {
            Optional<StripeWebhookEventStatus> status =
                    stripeWebhookEventTransaction.findEventStatus(
                            "evt_unknown"
                    );

            assertTrue(status.isEmpty());
        }
    }

    @Nested
    @DisplayName("Processing Management")
    class ProcessingManagementTests {

        @Test
        @DisplayName("Should transition RECEIVED event to PROCESSING")
        void shouldMarkEventAsProcessing() {
            createEvent(EVENT_ID);

            String token = startProcessing(EVENT_ID);

            StripeWebhookEvent event = stripeWebhookEventRepository
                    .findByEventId(EVENT_ID)
                    .orElseThrow();

            assertAll(
                    () -> assertEquals(
                            StripeWebhookEventStatus.PROCESSING,
                            event.getStatus()
                    ),
                    () -> assertNotNull(token),
                    () -> assertFalse(token.isBlank()),
                    () -> assertNotNull(event.getProcessingStartedAt())
            );
        }

        @Test
        @DisplayName("Should generate a unique processing token")
        void shouldGenerateUniqueProcessingToken() {
            createEvent(EVENT_ID);
            createEvent("evt_second_test");

            String firstToken = startProcessing(EVENT_ID);
            String secondToken = startProcessing("evt_second_test");

            assertNotEquals(firstToken, secondToken);
        }

        @Test
        @DisplayName("Should not process an already processing event")
        void shouldRejectDuplicateProcessingAttempt() {
            createEvent(EVENT_ID);

            startProcessing(EVENT_ID);

            Optional<String> secondAttempt =
                    stripeWebhookEventTransaction.tryMarkAsProcessing(EVENT_ID);

            assertTrue(secondAttempt.isEmpty());
        }

        @Test
        @DisplayName("Should return empty when event does not exist")
        void shouldReturnEmptyForUnknownEvent() {
            Optional<String> result =
                    stripeWebhookEventTransaction.tryMarkAsProcessing(
                            "evt_unknown"
                    );

            assertTrue(result.isEmpty());
        }
    }

    @Nested
    @DisplayName("Event Completion")
    class EventCompletionTests {

        @Test
        @DisplayName("Should mark event as PROCESSED")
        void shouldMarkEventAsProcessed() {
            createEvent(EVENT_ID);
            String token = startProcessing(EVENT_ID);

            boolean result =
                    stripeWebhookEventTransaction.markEventAsProcessed(
                            EVENT_ID,
                            token
                    );

            StripeWebhookEvent event = stripeWebhookEventRepository
                    .findByEventId(EVENT_ID)
                    .orElseThrow();

            assertAll(
                    () -> assertTrue(result),
                    () -> assertEquals(
                            StripeWebhookEventStatus.PROCESSED,
                            event.getStatus()
                    ),
                    () -> assertNotNull(event.getProcessedAt())
            );
        }

        @Test
        @DisplayName("Should reject PROCESSED update with wrong token")
        void shouldRejectProcessedUpdateWithWrongToken() {
            createEvent(EVENT_ID);
            startProcessing(EVENT_ID);

            boolean result =
                    stripeWebhookEventTransaction.markEventAsProcessed(
                            EVENT_ID,
                            "wrong-token"
                    );

            assertFalse(result);

            assertEquals(
                    StripeWebhookEventStatus.PROCESSING,
                    stripeWebhookEventRepository
                            .findByEventId(EVENT_ID)
                            .orElseThrow()
                            .getStatus()
            );
        }

        @Test
        @DisplayName("Should throw exception when completing unknown event")
        void shouldThrowWhenCompletingUnknownEvent() {
            assertThrows(
                    IllegalStateException.class,
                    () -> stripeWebhookEventTransaction.markEventAsProcessed(
                            "evt_unknown",
                            "token"
                    )
            );
        }
    }

    @Nested
    @DisplayName("Failure Management")
    class FailureManagementTests {

        @Test
        @DisplayName("Should mark event as FAILED")
        void shouldMarkEventAsFailed() {
            createEvent(EVENT_ID);
            String token = startProcessing(EVENT_ID);

            boolean result =
                    stripeWebhookEventTransaction.markEventAsFailed(
                            EVENT_ID,
                            token,
                            "Payment processing failed"
                    );

            StripeWebhookEvent event = stripeWebhookEventRepository
                    .findByEventId(EVENT_ID)
                    .orElseThrow();

            assertAll(
                    () -> assertTrue(result),
                    () -> assertEquals(
                            StripeWebhookEventStatus.FAILED,
                            event.getStatus()
                    ),
                    () -> assertEquals(
                            "Payment processing failed",
                            event.getErrorMessage()
                    )
            );
        }

        @Test
        @DisplayName("Should reject FAILED update with wrong token")
        void shouldRejectFailedUpdateWithWrongToken() {
            createEvent(EVENT_ID);
            startProcessing(EVENT_ID);

            boolean result =
                    stripeWebhookEventTransaction.markEventAsFailed(
                            EVENT_ID,
                            "wrong-token",
                            "Some error"
                    );

            assertFalse(result);

            assertEquals(
                    StripeWebhookEventStatus.PROCESSING,
                    stripeWebhookEventRepository
                            .findByEventId(EVENT_ID)
                            .orElseThrow()
                            .getStatus()
            );
        }

        @Test
        @DisplayName("Should mark event as REQUIRES_REVIEW")
        void shouldMarkEventAsRequiresReview() {
            createEvent(EVENT_ID);
            String token = startProcessing(EVENT_ID);

            boolean result =
                    stripeWebhookEventTransaction.markEventAsRequiresReview(
                            EVENT_ID,
                            token,
                            "Payment state conflict"
                    );

            StripeWebhookEvent event = stripeWebhookEventRepository
                    .findByEventId(EVENT_ID)
                    .orElseThrow();

            assertAll(
                    () -> assertTrue(result),
                    () -> assertEquals(
                            StripeWebhookEventStatus.REQUIRES_REVIEW,
                            event.getStatus()
                    ),
                    () -> assertEquals(
                            "Payment state conflict",
                            event.getErrorMessage()
                    )
            );
        }

        @Test
        @DisplayName("Should reject REQUIRES_REVIEW update with wrong token")
        void shouldRejectReviewUpdateWithWrongToken() {
            createEvent(EVENT_ID);
            startProcessing(EVENT_ID);

            boolean result =
                    stripeWebhookEventTransaction.markEventAsRequiresReview(
                            EVENT_ID,
                            "wrong-token",
                            "Payment conflict"
                    );

            assertFalse(result);

            assertEquals(
                    StripeWebhookEventStatus.PROCESSING,
                    stripeWebhookEventRepository
                            .findByEventId(EVENT_ID)
                            .orElseThrow()
                            .getStatus()
            );
        }
    }
}
