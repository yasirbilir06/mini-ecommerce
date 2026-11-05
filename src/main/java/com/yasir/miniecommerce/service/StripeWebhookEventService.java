
package com.yasir.miniecommerce.service;

import com.yasir.miniecommerce.dto.VerifiedStripeEvent;
import com.yasir.miniecommerce.exception.PaymentStateConflictException;
import com.yasir.miniecommerce.model.StripeWebhookEvent;
import com.yasir.miniecommerce.repository.StripeWebhookEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class StripeWebhookEventService {

    private static final Logger logger =
            LoggerFactory.getLogger(StripeWebhookEventService.class);

    private final StripeWebhookEventRepository stripeWebhookEventRepository;
    private final OrderService orderService;
    private final StripeWebhookEventTransaction stripeWebhookEventTransaction;

    public StripeWebhookEventService(
            StripeWebhookEventRepository stripeWebhookEventRepository,
            StripeWebhookEventTransaction stripeWebhookEventTransaction,
            OrderService orderService) {

        this.stripeWebhookEventRepository = stripeWebhookEventRepository;
        this.stripeWebhookEventTransaction = stripeWebhookEventTransaction;
        this.orderService = orderService;
    }

    public Optional<StripeWebhookEvent> findExistingEvent(String eventId) {
        return stripeWebhookEventRepository.findByEventId(eventId);
    }

    public StripeWebhookEvent registerEvent(
            String eventId,
            String eventType
    ) {
        return stripeWebhookEventTransaction.registerEvent(eventId, eventType);
    }

    public Optional<String> tryMarkAsProcessing(String eventId) {
        return stripeWebhookEventTransaction.tryMarkAsProcessing(eventId);
    }

    public boolean markEventAsProcessed(
            String eventId,
            String processingToken
    ) {
        return stripeWebhookEventTransaction.markEventAsProcessed(
                eventId,
                processingToken
        );
    }

    public boolean markEventAsFailed(
            String eventId,
            String processingToken,
            String errorMessage
    ) {
        return stripeWebhookEventTransaction.markEventAsFailed(
                eventId,
                processingToken,
                errorMessage
        );
    }

    public boolean markEventAsRequiresReview(
            String eventId,
            String processingToken,
            String errorMessage
    ) {
        return stripeWebhookEventTransaction.markEventAsRequiresReview(
                eventId,
                processingToken,
                errorMessage
        );
    }

    public void processVerifiedEvent(VerifiedStripeEvent verifiedEvent) {

        String eventId = verifiedEvent.eventId();
        String eventType = verifiedEvent.eventType();

        Optional<StripeWebhookEvent> existingEvent =
                findExistingEvent(eventId);

        if (existingEvent.isEmpty()) {
            try {
                registerEvent(eventId, eventType);

            } catch (DataIntegrityViolationException exception) {
                logger.debug(
                        "Webhook event zaten kaydedilmiş olabilir. Event ID: {}",
                        eventId
                );
            }
        }

        if (findExistingEvent(eventId).isEmpty()) {
            throw new IllegalStateException(
                    "Webhook event kaydedilemedi: " + eventId
            );
        }

        Optional<String> processingTokenOptional =
                tryMarkAsProcessing(eventId);

        if (processingTokenOptional.isEmpty()) {
            return;
        }

        String processingToken = processingTokenOptional.get();

        try {
            orderService.handlePaymentIntentSucceeded(
                    verifiedEvent.paymentIntent()
            );

            boolean markedAsProcessed =
                    markEventAsProcessed(eventId, processingToken);

            if (!markedAsProcessed) {
                logger.warn(
                        "Webhook event PROCESSED yapılamadı. "
                                + "İşlem sahipliği değişmiş olabilir. Event ID: {}",
                        eventId
                );
            }

        } catch (PaymentStateConflictException exception) {

            try {
                boolean markedAsRequiresReview =
                        markEventAsRequiresReview(
                                eventId,
                                processingToken,
                                exception.getMessage()
                        );

                if (!markedAsRequiresReview) {
                    logger.warn(
                            "Webhook event REQUIRES_REVIEW yapılamadı. "
                                    + "İşlem sahipliği değişmiş olabilir. Event ID: {}",
                            eventId
                    );
                }

            } catch (RuntimeException markingException) {
                logger.error(
                        "Webhook event REQUIRES_REVIEW olarak kaydedilemedi. Event ID: {}",
                        eventId,
                        markingException
                );
            }

            throw exception;

        } catch (RuntimeException exception) {

            try {
                boolean markedAsFailed =
                        markEventAsFailed(
                                eventId,
                                processingToken,
                                exception.getMessage()
                        );

                if (!markedAsFailed) {
                    logger.warn(
                            "Webhook event FAILED yapılamadı. "
                                    + "İşlem sahipliği değişmiş olabilir. Event ID: {}",
                            eventId
                    );
                }

            } catch (RuntimeException markingException) {
                logger.error(
                        "Webhook event FAILED olarak kaydedilemedi. Event ID: {}",
                        eventId,
                        markingException
                );
            }

            throw exception;
        }
    }
}
