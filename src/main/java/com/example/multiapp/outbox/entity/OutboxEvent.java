package com.example.multiapp.outbox.entity;

import com.example.multiapp.audit.entity.AuditLog;
import com.example.multiapp.common.event.DomainEventType;
import com.example.multiapp.common.jpa.CreatedOnlyEntity;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.EventListener;
import java.util.Objects;
import java.util.UUID;

/**
 * Outbox pattern:
 * - Produced inside the same DB transaction as the business change.
 * - Published asynchronously by a worker using SELECT ... FOR UPDATE SKIP LOCKED.
 * DDL (assumed):
 * tenant_id, id (PK), dedup_key, event_type, payload_json(jsonb),
 * status(NEW|SENT), attempts, next_attempt_at, created_at, sent_at, last_error
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@ToString(onlyExplicitlyIncluded = true)
@EqualsAndHashCode(onlyExplicitlyIncluded = true, callSuper = false)
@Entity
@Table(name = "outbox_event", schema = "app")
public class OutboxEvent extends CreatedOnlyEntity {
    @EmbeddedId
    @EqualsAndHashCode.Include
    private OutboxId id;

    @Column(name = "dedup_key")
    private String dedupKey;

    @Column(name = "event_type", nullable = false)
    private String eventType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload_json", nullable = false)
    private JsonNode payloadJson;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private OutboxStatus status;

    @Column(name = "attempts", nullable = false)
    private int attempts;

    @Column(name = "next_attempt_at")
    private OffsetDateTime nextAttemptAt;

    @Column(name = "sent_at")
    private OffsetDateTime sentAt;

    @Column(name = "last_error")
    private String lastError;

    public static OutboxEvent from(AuditLog auditLog, String dedupKey) {
        Objects.requireNonNull(auditLog, "auditLog");
        Objects.requireNonNull(dedupKey, "dedupKey");
        return OutboxEvent.newEvent(
                auditLog.getId().getTenantId(),
                auditLog.getAction(),
                auditLog.getDiffJson(),
                dedupKey
        );
    }

    private static OutboxEvent newEvent(UUID tenantId, String eventType,
                                       JsonNode payloadJson, String dedupKey) {
        Objects.requireNonNull(tenantId, "tenantId");
        requireNonBlank(eventType, "eventType");
//        Objects.requireNonNull(payloadJson, "payloadJson");

        var e = new OutboxEvent();
        e.id = new OutboxId(tenantId, UUID.randomUUID());
        e.eventType = eventType;
        e.payloadJson = payloadJson;
        e.dedupKey = normalizeOptional(dedupKey);
        e.status = OutboxStatus.NEW;
        e.attempts = 0;
        e.nextAttemptAt = null;
        e.sentAt = null;
        e.lastError = null;
        return e;
    }

    /** Whether this event is eligible for publishing at time {@code now}. */
    public boolean isDue(OffsetDateTime now) {
        Objects.requireNonNull(now, "now");
        if (status != OutboxStatus.NEW) return false;
        return nextAttemptAt == null || !nextAttemptAt.isAfter(now);
    }

    /** Mark publish success. */
    public void markSent(OffsetDateTime now) {
        Objects.requireNonNull(now, "now");
        transitionTo(OutboxStatus.SENT);
        this.sentAt = now;
        this.nextAttemptAt = null;
        this.lastError = null;
    }

    /**
     * Record failure and schedule retry.
     * Keep status as NEW (no FAILED state), rely on nextAttemptAt to prevent hot-loop retries.
     */
    public void markFailedAndScheduleRetry(String error, OffsetDateTime nextAttemptAt) {
        this.attempts += 1;
        this.lastError = normalizeOptional(error);
        this.nextAttemptAt = Objects.requireNonNull(nextAttemptAt, "nextAttemptAt");
    }

    /** Optional helper if you want a hard stop rule at worker level. */
    public void reachMaxAttempts(String error) {
        transitionTo(OutboxStatus.DEAD);
        this.lastError = normalizeOptional(error);
        this.nextAttemptAt = null;
    }

    private void transitionTo(OutboxStatus target) {
        Objects.requireNonNull(target, "target");
        OutboxStatus from = Objects.requireNonNull(this.status, "status");
        if(from == target) return;
        if(!OutboxStatus.isAllowedTransition(from, target)) {
            throw new IllegalStateException("Invalid outbox status transition: "
                    + from + " -> " + target);
        }
    }

    private static String normalizeOptional(String s) {
        return (s == null || s.isBlank()) ? null : s.strip();
    }

    private static void requireNonBlank(String s, String name) {
        if (s == null || s.isBlank())
            throw new IllegalArgumentException(name + " must be non-blank");
    }
}
