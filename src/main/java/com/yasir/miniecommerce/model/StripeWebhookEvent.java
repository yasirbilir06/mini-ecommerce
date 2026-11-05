
package com.yasir.miniecommerce.model;

import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.Objects;

@Entity
@Table(name = "stripe_webhook_events")
public class StripeWebhookEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", nullable = false, unique = true, length = 255)
    private String eventId;

    @Column(name = "event_type", nullable = false, length = 100)
    private String eventType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StripeWebhookEventStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "processing_started_at")
    private LocalDateTime processingStartedAt;

    @Column(name = "processing_token", length = 36)
    private String processingToken;

    @Column(name = "processed_at")
    private LocalDateTime processedAt;

    @Column(name = "error_message", length = 1000)
    private String errorMessage;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    public StripeWebhookEvent(String eventId, String eventType) {
        this.eventId = eventId;
        this.eventType = eventType;
        this.status = StripeWebhookEventStatus.RECEIVED;
    }

    protected StripeWebhookEvent() {
    }

    public void markAsProcessing(String processingToken) {
        this.status = StripeWebhookEventStatus.PROCESSING;
        this.processingStartedAt = LocalDateTime.now();
        this.processingToken = processingToken;
        this.errorMessage = null;
    }

    public boolean markAsProcessed(String processingToken) {
        if (status != StripeWebhookEventStatus.PROCESSING
                || !Objects.equals(this.processingToken, processingToken)) {
            return false;
        }

        this.status = StripeWebhookEventStatus.PROCESSED;
        this.processedAt = LocalDateTime.now();
        this.processingStartedAt = null;
        this.processingToken = null;
        this.errorMessage = null;

        return true;
    }

    public boolean markAsFailed(
            String processingToken,
            String errorMessage
    ) {
        if (status != StripeWebhookEventStatus.PROCESSING
                || !Objects.equals(this.processingToken, processingToken)) {
            return false;
        }

        this.status = StripeWebhookEventStatus.FAILED;
        this.processingStartedAt = null;
        this.processingToken = null;
        this.errorMessage = truncateErrorMessage(errorMessage);

        return true;
    }

    public boolean markAsRequiresReview(
            String processingToken,
            String errorMessage
    ) {
        if (status != StripeWebhookEventStatus.PROCESSING
                || !Objects.equals(this.processingToken, processingToken)) {
            return false;
        }

        this.status = StripeWebhookEventStatus.REQUIRES_REVIEW;
        this.processingStartedAt = null;
        this.processingToken = null;
        this.errorMessage = truncateErrorMessage(errorMessage);

        return true;
    }

    public boolean canBeProcessedAgain(LocalDateTime staleBefore) {
        if (status == StripeWebhookEventStatus.RECEIVED
                || status == StripeWebhookEventStatus.FAILED) {
            return true;
        }

        return status == StripeWebhookEventStatus.PROCESSING
                && processingStartedAt != null
                && processingStartedAt.isBefore(staleBefore);
    }

    private String truncateErrorMessage(String message) {
        if (message == null) {
            return null;
        }

        return message.length() > 1000
                ? message.substring(0, 1000)
                : message;
    }

    public Long getId() {
        return id;
    }

    public String getEventId() {
        return eventId;
    }

    public String getEventType() {
        return eventType;
    }

    public StripeWebhookEventStatus getStatus() {
        return status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getProcessingStartedAt() {
        return processingStartedAt;
    }

    public LocalDateTime getProcessedAt() {
        return processedAt;
    }

    public String getErrorMessage() {
        return errorMessage;
    }
}
