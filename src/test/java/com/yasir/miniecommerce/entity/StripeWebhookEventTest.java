
package com.yasir.miniecommerce.entity;

import com.yasir.miniecommerce.model.StripeWebhookEvent;
import com.yasir.miniecommerce.model.StripeWebhookEventStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Stripe Webhook Event Entity Tests")
class StripeWebhookEventTest {

    private static final String EVENT_ID = "evt_test_123";
    private static final String EVENT_TYPE = "payment_intent.succeeded";

    private StripeWebhookEvent createEvent() {
        return new StripeWebhookEvent(EVENT_ID, EVENT_TYPE);
    }

    @Nested
    @DisplayName("Event Creation")
    class EventCreationTests {

        @Test
        @DisplayName("Should create event with RECEIVED status")
        void shouldCreateEventWithReceivedStatus() {
            StripeWebhookEvent event = createEvent();

            assertAll(
                    () -> assertEquals(EVENT_ID, event.getEventId()),
                    () -> assertEquals(EVENT_TYPE, event.getEventType()),
                    () -> assertEquals(
                            StripeWebhookEventStatus.RECEIVED,
                            event.getStatus()
                    ),
                    () -> assertNull(event.getProcessedAt()),
                    () -> assertNull(event.getProcessingStartedAt()),
                    () -> assertNull(event.getErrorMessage())
            );
        }
    }

    @Nested
    @DisplayName("Processing State")
    class ProcessingStateTests {

        @Test
        @DisplayName("Should transition to PROCESSING")
        void shouldMarkEventAsProcessing() {
            StripeWebhookEvent event = createEvent();
            String token = UUID.randomUUID().toString();

            event.markAsProcessing(token);

            assertAll(
                    () -> assertEquals(
                            StripeWebhookEventStatus.PROCESSING,
                            event.getStatus()
                    ),
                    () -> assertNotNull(event.getProcessingStartedAt()),
                    () -> assertNull(event.getProcessedAt()),
                    () -> assertNull(event.getErrorMessage())
            );
        }

        @Test
        @DisplayName("Should set processing timestamp")
        void shouldSetProcessingStartedAt() {
            StripeWebhookEvent event = createEvent();

            LocalDateTime before = LocalDateTime.now();
            event.markAsProcessing("token-123");
            LocalDateTime after = LocalDateTime.now();

            assertNotNull(event.getProcessingStartedAt());
            assertFalse(event.getProcessingStartedAt().isBefore(before));
            assertFalse(event.getProcessingStartedAt().isAfter(after));
        }
    }

    @Nested
    @DisplayName("Successful Processing")
    class SuccessfulProcessingTests {

        @Test
        @DisplayName("Should transition to PROCESSED with matching token")
        void shouldMarkEventAsProcessed() {
            StripeWebhookEvent event = createEvent();
            String token = UUID.randomUUID().toString();

            event.markAsProcessing(token);
            boolean result = event.markAsProcessed(token);

            assertAll(
                    () -> assertTrue(result),
                    () -> assertEquals(
                            StripeWebhookEventStatus.PROCESSED,
                            event.getStatus()
                    ),
                    () -> assertNotNull(event.getProcessedAt()),
                    () -> assertNull(event.getProcessingStartedAt()),
                    () -> assertNull(event.getErrorMessage())
            );
        }

        @Test
        @DisplayName("Should reject completion with wrong token")
        void shouldRejectProcessedStatusWithWrongToken() {
            StripeWebhookEvent event = createEvent();

            event.markAsProcessing("correct-token");
            boolean result = event.markAsProcessed("wrong-token");

            assertAll(
                    () -> assertFalse(result),
                    () -> assertEquals(
                            StripeWebhookEventStatus.PROCESSING,
                            event.getStatus()
                    ),
                    () -> assertNull(event.getProcessedAt())
            );
        }

        @Test
        @DisplayName("Should reject completion when event is not PROCESSING")
        void shouldRejectCompletionFromReceivedStatus() {
            StripeWebhookEvent event = createEvent();

            boolean result = event.markAsProcessed("some-token");

            assertFalse(result);
            assertEquals(
                    StripeWebhookEventStatus.RECEIVED,
                    event.getStatus()
            );
        }
    }

    @Nested
    @DisplayName("Failed Processing")
    class FailedProcessingTests {

        @Test
        @DisplayName("Should transition to FAILED")
        void shouldMarkEventAsFailed() {
            StripeWebhookEvent event = createEvent();
            String token = "token-123";

            event.markAsProcessing(token);
            boolean result = event.markAsFailed(
                    token,
                    "Payment processing failed"
            );

            assertAll(
                    () -> assertTrue(result),
                    () -> assertEquals(
                            StripeWebhookEventStatus.FAILED,
                            event.getStatus()
                    ),
                    () -> assertEquals(
                            "Payment processing failed",
                            event.getErrorMessage()
                    ),
                    () -> assertNull(event.getProcessingStartedAt()),
                    () -> assertNull(event.getProcessedAt())
            );
        }

        @Test
        @DisplayName("Should reject failure update with wrong token")
        void shouldRejectFailureWithWrongToken() {
            StripeWebhookEvent event = createEvent();

            event.markAsProcessing("correct-token");
            boolean result = event.markAsFailed(
                    "wrong-token",
                    "Some error"
            );

            assertAll(
                    () -> assertFalse(result),
                    () -> assertEquals(
                            StripeWebhookEventStatus.PROCESSING,
                            event.getStatus()
                    ),
                    () -> assertNull(event.getErrorMessage())
            );
        }

        @Test
        @DisplayName("Should truncate error message to 1000 characters")
        void shouldTruncateLongErrorMessage() {
            StripeWebhookEvent event = createEvent();
            String token = "token-123";
            String longMessage = "E".repeat(1500);

            event.markAsProcessing(token);
            boolean result = event.markAsFailed(token, longMessage);

            assertAll(
                    () -> assertTrue(result),
                    () -> assertEquals(
                            StripeWebhookEventStatus.FAILED,
                            event.getStatus()
                    ),
                    () -> assertNotNull(event.getErrorMessage()),
                    () -> assertEquals(1000, event.getErrorMessage().length())
            );
        }
    }

    @Nested
    @DisplayName("Manual Review")
    class ManualReviewTests {

        @Test
        @DisplayName("Should transition to REQUIRES_REVIEW")
        void shouldMarkEventAsRequiresReview() {
            StripeWebhookEvent event = createEvent();
            String token = "token-123";

            event.markAsProcessing(token);
            boolean result = event.markAsRequiresReview(
                    token,
                    "Payment state conflict"
            );

            assertAll(
                    () -> assertTrue(result),
                    () -> assertEquals(
                            StripeWebhookEventStatus.REQUIRES_REVIEW,
                            event.getStatus()
                    ),
                    () -> assertEquals(
                            "Payment state conflict",
                            event.getErrorMessage()
                    ),
                    () -> assertNull(event.getProcessingStartedAt()),
                    () -> assertNull(event.getProcessedAt())
            );
        }

        @Test
        @DisplayName("Should reject review update with wrong token")
        void shouldRejectReviewWithWrongToken() {
            StripeWebhookEvent event = createEvent();

            event.markAsProcessing("correct-token");
            boolean result = event.markAsRequiresReview(
                    "wrong-token",
                    "Payment conflict"
            );

            assertFalse(result);
            assertEquals(
                    StripeWebhookEventStatus.PROCESSING,
                    event.getStatus()
            );
        }
    }

    @Nested
    @DisplayName("Retry Eligibility")
    class RetryEligibilityTests {

        @Test
        @DisplayName("Should allow retry for RECEIVED event")
        void shouldAllowRetryForReceivedEvent() {
            StripeWebhookEvent event = createEvent();

            assertTrue(event.canBeProcessedAgain(LocalDateTime.now()));
        }

        @Test
        @DisplayName("Should allow retry for FAILED event")
        void shouldAllowRetryForFailedEvent() {
            StripeWebhookEvent event = createEvent();

            event.markAsProcessing("token");
            event.markAsFailed("token", "Temporary failure");

            assertTrue(event.canBeProcessedAgain(LocalDateTime.now()));
        }

        @Test
        @DisplayName("Should not allow retry for PROCESSED event")
        void shouldNotAllowRetryForProcessedEvent() {
            StripeWebhookEvent event = createEvent();

            event.markAsProcessing("token");
            event.markAsProcessed("token");

            assertFalse(event.canBeProcessedAgain(LocalDateTime.now()));
        }

        @Test
        @DisplayName("Should not allow retry for REQUIRES_REVIEW event")
        void shouldNotAllowRetryForReviewEvent() {
            StripeWebhookEvent event = createEvent();

            event.markAsProcessing("token");
            event.markAsRequiresReview(tokenOrDefault(), "Needs manual review");

            assertFalse(event.canBeProcessedAgain(LocalDateTime.now()));
        }

        @Test
        @DisplayName("Should not allow immediate retry for PROCESSING event")
        void shouldNotAllowImmediateRetryForProcessingEvent() {
            StripeWebhookEvent event = createEvent();

            event.markAsProcessing("token");

            LocalDateTime staleBefore = LocalDateTime.now().minusMinutes(5);

            assertFalse(event.canBeProcessedAgain(staleBefore));
        }

        @Test
        @DisplayName("Should allow retry for stale PROCESSING event")
        void shouldAllowRetryForStaleProcessingEvent() {
            StripeWebhookEvent event = createEvent();

            event.markAsProcessing("token");

            LocalDateTime staleBefore =
                    event.getProcessingStartedAt().plusMinutes(5);

            assertTrue(event.canBeProcessedAgain(staleBefore));
        }
    }

    private String tokenOrDefault() {
        return "token";
    }
}
