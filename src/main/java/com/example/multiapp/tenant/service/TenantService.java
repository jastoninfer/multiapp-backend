package com.example.multiapp.tenant.service;

import com.example.multiapp.audit.entity.AuditLog;
import com.example.multiapp.audit.repo.AuditLogRepository;
import com.example.multiapp.common.aduit.AuditPayloadBuilder;
import com.example.multiapp.common.aduit.AuditWriter;
import com.example.multiapp.common.api.NotFoundException;
import com.example.multiapp.common.api.PageNormalizer;
import com.example.multiapp.common.crypto.Hashing;
import com.example.multiapp.common.event.DomainEventPayloads;
import com.example.multiapp.common.event.DomainEventType;
import com.example.multiapp.common.outbox.DedupKeyFactory;
import com.example.multiapp.common.outbox.OutboxPublisher;
import com.example.multiapp.common.tenant.RequestContext;
import com.example.multiapp.common.web.IdempotencyConflictException;
import com.example.multiapp.idempotency.codec.IdempotencyResponseCodec;
import com.example.multiapp.idempotency.entity.IdempotencyId;
import com.example.multiapp.idempotency.entity.IdempotencyRecord;
import com.example.multiapp.idempotency.model.IdempotencyStatus;
import com.example.multiapp.idempotency.repo.IdempotencyRecordRepository;
import com.example.multiapp.idempotency.service.IdempotencyService;
import com.example.multiapp.outbox.entity.OutboxEvent;
import com.example.multiapp.outbox.entity.OutboxStatus;
import com.example.multiapp.outbox.repo.OutboxEventRepository;
import com.example.multiapp.outbox.service.OutboxEventService;
import com.example.multiapp.tenant.auth.TenantAction;
import com.example.multiapp.tenant.auth.TenantActionPayload;
import com.example.multiapp.tenant.auth.TenantAuthorizer;
import com.example.multiapp.tenant.dto.CreateTenantRequest;
import com.example.multiapp.tenant.dto.TenantResponse;
import com.example.multiapp.tenant.dto.UpdateTenantRequest;
import com.example.multiapp.tenant.entity.Tenant;
import com.example.multiapp.tenant.event.TenantEventType;
import com.example.multiapp.tenant.model.TenantStatus;
import com.example.multiapp.tenant.repo.TenantRepository;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TenantService {
    private final TenantRepository tenantRepo;
    private final AuditWriter auditWriter;
//    private final AuditLogRepository auditRepo;
//    private final OutboxEventRepository outboxRepo;
    private final TenantAuthorizer tenantAuth;
    private final IdempotencyService idemService;
//    private final IdempotencyResponseCodec codec;
//    private final IdempotencyRecordRepository idemRepo;
//    private final OutboxEventService outboxService;
    private final OutboxPublisher outboxPublisher;
    // 查看租户详情, 只有属于该租户的成员以及超级管理员才有权限查看
    // 不保证结果存在
    @Transactional(readOnly = true)
    public TenantResponse getById(RequestContext ctx) {
        UUID tenantId = ctx.tenantId();
        Objects.requireNonNull(tenantId, "tenant.id");
        tenantAuth.require(ctx, TenantAction.READ, new TenantActionPayload(tenantId));
//        tenantAuth.require(ctx, tenantId, TenantAction.READ);
        Tenant tenant = tenantRepo.findById(tenantId).orElseThrow(
                () -> new NotFoundException("tenant not found"));
        return TenantResponse.from(tenant);
    }

    // 创建新租户
    // 需要考虑幂等
    @Transactional
    public TenantResponse create(RequestContext ctx, @NotBlank String idemKey,
                                 CreateTenantRequest req) {
        Objects.requireNonNull(ctx, "ctx");
        Objects.requireNonNull(idemKey, "Idempotency-Key");
        Objects.requireNonNull(req, "req");
        tenantAuth.require(ctx, TenantAction.CREATE);
//        tenantAuth.requireCreate(ctx);
        final String requestHash = Hashing.sha256Hex(req.toStableString());
        Tenant tenant = Tenant.create(req.name());
        final UUID tenantId = tenant.getId();
        final UUID actorUserId = ctx.userId();
        if(tenantRepo.existsByNameCi(tenant.getName()))
            throw new DataIntegrityViolationException("Tenant name taken");
        final IdempotencyId idemId = new IdempotencyId(tenantId, actorUserId, idemKey);
        // 插入失败, 但有缓存才有返回值
        Optional<TenantResponse> cached = idemService.tryInsert(tenantId, actorUserId, idemKey,
                        requestHash, TenantResponse.class);
        if(cached.isPresent()) return cached.get();
        tenantRepo.save(tenant);
        AuditLog auditLog = AuditLog.tenantCreated(tenantId, actorUserId, ctx.requestId());
        auditWriter.append(auditLog);
//        auditRepo.save();
        // 更新outbox, dedupKey包含actor+eventType
        DomainEventType eventType = TenantEventType.TENANT_CREATED;
//        String dedupKey = idemKey + ":" + eventType.key();
        outboxPublisher.publish(OutboxEvent.from(auditLog, DedupKeyFactory.forCreate(idemKey, eventType)));
//        outboxRepo.insertDedupNew(tenantId, outboxEvent.getId().getId(),
//                dedupKey, eventType.key(), codec.write(outboxEvent.getPayloadJson()));
        // 暂时不做tenant表本身的幂等
        TenantResponse resp = TenantResponse.from(tenant);
//        String respJson = codec.write(resp);
        idemService.tryComplete(tenantId, actorUserId, idemKey, requestHash, resp);
        return resp;
    }

    @Transactional(readOnly = true)
    public Page<TenantResponse> list(RequestContext ctx, Pageable pageable) {
        tenantAuth.require(ctx, TenantAction.LIST);
//        tenantAuth.requireList(ctx);
        Pageable p = PageNormalizer.normalize(pageable, 100, 20,
                Sort.by(Sort.Order.desc("updatedAt"), Sort.Order.desc("id")),
                Set.of("updatedAt", "id", "name"));
        return tenantRepo.findAll(pageable).map(TenantResponse::from);
    }

    // 暂不考虑并发控制
    // 调整租户状态
    @Transactional
    public TenantResponse transition(RequestContext ctx, TenantStatus toStatus) {
        Objects.requireNonNull(ctx, "ctx");
        UUID tenantId = ctx.tenantId();
        Objects.requireNonNull(ctx.requestId(), "ctx.requestId");
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(toStatus, "toStatus");
//        tenantAuth.requireChangeStatus(ctx);
        tenantAuth.require(ctx, TenantAction.CHANGE_STATUS);
        Tenant tenant = tenantRepo.findById(tenantId).
                orElseThrow(() -> new NotFoundException("Tenant not found"));
        TenantStatus fromStatus = Objects.requireNonNull(tenant.getStatus(), "tenant.status");
        if(fromStatus == toStatus) {
            return TenantResponse.from(tenant);
        }
        tenant.transitionTo(toStatus);
        DomainEventType eventType = TenantEventType.TENANT_STATUS_CHANGED;
        JsonNode payloadData = AuditPayloadBuilder.forEntity(tenantId, eventType)
                .addField("status", fromStatus.name(), toStatus.name()).build();
//        JsonNode payload = DomainEventPayloads.envelop(tenantId, ctx.userId(), ctx.requestId(),
//                1, payloadData);
        AuditLog auditLog = AuditLog.tenantStatusChanged(tenantId,
                ctx.userId(), DomainEventPayloads.envelopFrom(ctx, tenantId, payloadData),
                ctx.requestId());
//        auditRepo.save();
        auditWriter.append(auditLog);
//        String dedupKey = ctx.requestId() + ":" + eventType.key();
        outboxPublisher.publish(OutboxEvent.from(auditLog, DedupKeyFactory.forRequestScopedUpdate(
                ctx.requestId(), eventType)));
//        outboxRepo.insertDedupNew(tenantId, outboxEvent.getId().getId(), dedupKey,
//                eventType.key(), codec.write(outboxEvent.getPayloadJson()));
        return TenantResponse.from(tenant);
    }

    // 暂不考虑并发控制
    // 只能更新租户名
    @Transactional
    public TenantResponse update(RequestContext ctx, UpdateTenantRequest req) {
        Objects.requireNonNull(ctx, "ctx");
        UUID tenantId = ctx.tenantId();
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(req, "req");
        tenantAuth.require(ctx, TenantAction.UPDATE, new TenantActionPayload(tenantId));
//        tenantAuth.require(ctx, tenantId, TenantAction.UPDATE);
        Tenant tenant = tenantRepo.findById(tenantId).
                orElseThrow(() -> new NotFoundException("Tenant not found"));
        DomainEventType eventType = TenantEventType.TENANT_UPDATED;
        AuditPayloadBuilder payloadBuilder = AuditPayloadBuilder.forEntity(tenantId,
                eventType);
        // 考察可能会更新的各个字段
        boolean changed = false;
        if(req.name() != null && !req.name().isBlank()) {
            String newName = req.name().strip();
            if(newName.equals(tenant.getName())){
                throw new IllegalArgumentException("name already in use");
            }
            payloadBuilder.addField("name", tenant.getName(), newName);
            tenant.updateName(newName);
            changed = true;
        }
        if(!changed) return TenantResponse.from(tenant);
//        if(changed) auditRepo.save(AuditLog.tenantUpdated(tenantId, ctx.userId(),
//                payloadBuilder.build(), ctx.requestId()));
        AuditLog auditLog = AuditLog.tenantUpdated(tenantId, ctx.userId(),
                payloadBuilder.build(), ctx.requestId());
        auditWriter.append(auditLog);
        outboxPublisher.publish(OutboxEvent.from(auditLog, DedupKeyFactory.forRequestScopedUpdate(
                ctx.requestId(), eventType)));
        return TenantResponse.from(tenant);
    }
}
