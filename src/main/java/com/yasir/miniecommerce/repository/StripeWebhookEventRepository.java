package com.yasir.miniecommerce.repository;

import com.yasir.miniecommerce.model.StripeWebhookEvent;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface StripeWebhookEventRepository
        extends JpaRepository<StripeWebhookEvent, Long> {

    Optional<StripeWebhookEvent> findByEventId(String eventId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT event
            FROM StripeWebhookEvent event
            WHERE event.eventId = :eventId
            """)
    Optional<StripeWebhookEvent> findByEventIdWithLock(
            @Param("eventId") String eventId
    );
}