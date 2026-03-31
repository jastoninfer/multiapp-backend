package com.example.multiapp.outbox.repo;

import com.example.multiapp.outbox.entity.OutboxEvent;
import com.example.multiapp.outbox.entity.OutboxId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.security.core.parameters.P;

import java.util.List;
import java.util.UUID;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, OutboxId> {
    @Modifying
    @Query(value = """
    insert into app.outbox_event(
        tenant_id, id, dedup_key, event_type, payload_json,
        status, attempts, next_attempt_at, sent_at, last_error
    ) values (:tenantId, :id, :dedupKey, :eventType,
            cast(:payloadJson as jsonb), 'NEW', 0, null, null, null
    ) on conflict (tenant_id, event_type, dedup_key) do nothing
    """, nativeQuery = true)
    int insertDedupNew(
            @Param("tenantId")UUID tenantId,
            @Param("id") UUID id,
            @Param("dedupKey") String dedupKey,
            @Param("eventType") String eventType,
            @Param("payloadJson") String payloadJson
    );

    /*
    * Spring Data JPA 很难优雅表达 FOR UPDATE SKIP LOCKED
    * 建议在 repo 里再加一个 native query 用于 worker 批量拉取
    * */
    @Query(value = """
    select * from app.outbox_event
    where tenant_id = :tenantId and status = 'NEW'
          and (next_attempt_at is null or next_attempt_at <= now())
    order by created_at
    for update skip locked
    limit :batchSize
    """, nativeQuery = true)
    List<OutboxEvent> lockNextBatchForPublishing(
            @Param("tenantId") UUID tenantId,
            @Param("batchSize") int batchSize
    );
}
