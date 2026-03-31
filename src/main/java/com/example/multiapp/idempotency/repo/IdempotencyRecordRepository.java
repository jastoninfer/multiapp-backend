package com.example.multiapp.idempotency.repo;

import com.example.multiapp.idempotency.entity.IdempotencyId;
import com.example.multiapp.idempotency.entity.IdempotencyRecord;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.security.core.parameters.P;

import java.util.UUID;

public interface IdempotencyRecordRepository extends JpaRepository<IdempotencyRecord, IdempotencyId> {
    @Modifying
    @Query(value = """
        insert into app.idempotency_record(tenant_id, actor_user_id, idempotency_key, request_hash, response_json, status)
        values (:tenantId, :actorUserId, :idemKey, :requestHash, cast(:responseJson as jsonb), 'IN_PROGRESS')
        on conflict (tenant_id, actor_user_id, idempotency_key) do nothing
        """, nativeQuery = true)
    /*
    * tryInsert==1: 你是赢家->创建ticket->complete(...)==1
    * tryInsert==0: 查record
    *   - hash不同: 409
    *   - status COMPLETED: 返回responseJson
    *   - status IN_PROGRESS: 返回409/202 (二选一)
    * */
    int tryInsert(@Param("tenantId") UUID tenantId,
                  @Param("actorUserId") UUID actorUserId,
                  @Param("idemKey") String idemKey,
                  @Param("requestHash") String requestHash);

    @Modifying
    @Query("""
        update IdempotencyRecord r
            set r.status = com.example.multiapp.idempotency.model.IdempotencyStatus.COMPLETED,
                r.responseJson = :responseJson
        where r.id = :id
            and r.requestHash = :requestHash
            and r.status = com.example.multiapp.idempotency.model.IdempotencyStatus.IN_PROGRESS
    """)
    int complete(@Param("id") IdempotencyId id,
                 @Param("requestHash") String requestHash,
                 @Param("responseJson") String responseJson);
}
