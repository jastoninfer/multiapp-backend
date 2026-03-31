package com.example.multiapp.idempotency.service;

import com.example.multiapp.common.web.IdempotencyConflictException;
import com.example.multiapp.idempotency.codec.IdempotencyResponseCodec;
import com.example.multiapp.idempotency.entity.IdempotencyId;
import com.example.multiapp.idempotency.entity.IdempotencyRecord;
import com.example.multiapp.idempotency.model.IdempotencyStatus;
import com.example.multiapp.idempotency.repo.IdempotencyRecordRepository;
import com.example.multiapp.tenant.dto.TenantResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class IdempotencyService {
    private final IdempotencyRecordRepository idemRepo;
    private final IdempotencyResponseCodec codec;

    @Transactional
    public <T> void tryComplete(UUID tenantId, UUID actorUserId, String idemKey,
                         String requestHash, T resp) {
        Objects.requireNonNull(requestHash, "requestHash");
        int updated = idemRepo.complete(new IdempotencyId(tenantId,
                actorUserId, idemKey), requestHash, codec.write(resp));
        if(updated != 1) {
            // shouldn't happen
            throw new IllegalStateException("Idempotency completion failed");
        }
    }

    /*
    * 这个方法, 如果插入成功返回Optional.empty()
    * 如果插入失败, 但找到了命中的记录, 返回命中记录的Response, 以泛型提供
    * 如果插入失败, 没有找到命中记录, 抛出异常
    * */
    @Transactional
    public <T> Optional<T> tryInsert(UUID tenantId, UUID actorUserId, String idemKey,
                              String requestHash, Class<T> responseType) {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(actorUserId, "actorUserId");
        Objects.requireNonNull(idemKey, "idemKey");
        Objects.requireNonNull(requestHash, "requestHash");
        int inserted = idemRepo.tryInsert(tenantId, actorUserId, idemKey, requestHash);
        // insert 返回值1或0
        if (inserted == 1) return Optional.empty();
        IdempotencyRecord existing = idemRepo.findById(new IdempotencyId(tenantId, actorUserId, idemKey))
                .orElseThrow(() -> new IllegalStateException("Idempotency record missing after conflict"));
        if(!existing.getRequestHash().equals(requestHash)) {
            throw new IdempotencyConflictException("Idempotency-Key reused with different request body");
        }
        if(existing.getStatus() == IdempotencyStatus.COMPLETED) {
            T resp = codec.read(existing.getResponseJson(), responseType);
            return Optional.of(resp);
        }
        throw new IdempotencyConflictException("Request in progress");
    }
}
