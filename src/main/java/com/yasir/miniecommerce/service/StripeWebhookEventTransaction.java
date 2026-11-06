package com.yasir.miniecommerce.service;

import com.yasir.miniecommerce.model.StripeWebhookEventStatus;
import com.yasir.miniecommerce.repository.StripeWebhookEventRepository;
import org.springframework.stereotype.Service;
import com.yasir.miniecommerce.model.StripeWebhookEvent;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;


import java.time.LocalDateTime;
import java.util.Optional;

@Service
public class StripeWebhookEventTransaction {

    private final StripeWebhookEventRepository stripeWebhookEventRepository;

    public StripeWebhookEventTransaction(
            StripeWebhookEventRepository stripeWebhookEventRepository) {
        this.stripeWebhookEventRepository = stripeWebhookEventRepository;
    }
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public StripeWebhookEvent registerEvent(String eventId, String eventType) {

        StripeWebhookEvent event = new StripeWebhookEvent(eventId, eventType);

        return stripeWebhookEventRepository.saveAndFlush(event);
    }
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<String> tryMarkAsProcessing(String eventId) {

        Optional<StripeWebhookEvent> eventOptional =
                stripeWebhookEventRepository.findByEventIdWithLock(eventId);

        if (eventOptional.isEmpty()) {
            return Optional.empty();
        }

        StripeWebhookEvent event = eventOptional.get();

        LocalDateTime staleBefore = LocalDateTime.now().minusMinutes(5);

        if (!event.canBeProcessedAgain(staleBefore)) {
            return Optional.empty();
        }

        String processingToken = UUID.randomUUID().toString();

        event.markAsProcessing(processingToken);

        return Optional.of(processingToken);
    }
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean markEventAsProcessed(
            String eventId,
            String processingToken
    ) {

        StripeWebhookEvent event = stripeWebhookEventRepository
                .findByEventIdWithLock(eventId)
                .orElseThrow(() -> new IllegalStateException(
                        "Webhook event bulunamadı: " + eventId
                ));

        return event.markAsProcessed(processingToken);
    }
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean markEventAsFailed(
            String eventId,
            String processingToken,
            String errorMessage
    ){

        StripeWebhookEvent event = stripeWebhookEventRepository
                .findByEventIdWithLock(eventId)
                .orElseThrow(() -> new IllegalStateException(
                        "Webhook event bulunamadı: " + eventId
                ));

        return event.markAsFailed(processingToken, errorMessage);
    }
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean markEventAsRequiresReview(
            String eventId,
            String processingToken,
            String errorMessage
    ) {

        StripeWebhookEvent event = stripeWebhookEventRepository
                .findByEventIdWithLock(eventId)
                .orElseThrow(() -> new IllegalStateException(
                        "Webhook event bulunamadı: " + eventId
                ));

        return event.markAsRequiresReview(processingToken, errorMessage);
    }
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public Optional<StripeWebhookEventStatus> findEventStatus(String eventId) {

        return stripeWebhookEventRepository.findByEventId(eventId)
                .map(StripeWebhookEvent::getStatus);
    }
}