package com.example.multiapp.resource.service;

import com.example.multiapp.audit.entity.AuditLog;
import com.example.multiapp.audit.model.AuditEntityType;
import com.example.multiapp.common.aduit.AuditPayloadBuilder;
import com.example.multiapp.common.aduit.AuditWriter;
import com.example.multiapp.common.api.NotFoundException;
import com.example.multiapp.common.event.DomainEventPayloads;
import com.example.multiapp.common.event.DomainEventType;
import com.example.multiapp.common.tenant.RequestContext;
import com.example.multiapp.common.web.IfMatchPreconditions;
import com.example.multiapp.resource.auth.ResourceBlockAuthorizer;
import com.example.multiapp.resource.dto.CreateResourceBlockRequest;
import com.example.multiapp.resource.dto.ResourceBlockResponse;
import com.example.multiapp.resource.entity.ResourceBlock;
import com.example.multiapp.resource.model.ResourceBlockEvent;
import com.example.multiapp.resource.repo.ResourceBlockRepository;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ResourceBlockService {
    private final ResourceBlockRepository resourceBlockRepo;
    private final ResourceBlockAuthorizer resourceBlockAuth;
    private final AuditWriter auditWriter;

    @Transactional
    public ResourceBlockResponse create(RequestContext ctx, UUID resourceUserId, CreateResourceBlockRequest req) {
        Objects.requireNonNull(ctx, "ctx");
        Objects.requireNonNull(resourceUserId, "resourceUserId");
        Objects.requireNonNull(req, "CreateResourceBlockRequest");
        resourceBlockAuth.requireCreate(ctx, resourceUserId, req);
        validateResourceBlockDuration(req.startAt(), req.endAt());
        ResourceBlock b = ResourceBlock.from(ctx.tenantId(), resourceUserId, req);
        DomainEventType eventType = ResourceBlockEvent.RESOURCE_BLOCK_CREATED;
        resourceBlockRepo.save(b);
        UUID resourceBlockId = b.getId().getId();
        JsonNode payloadData = AuditPayloadBuilder.forEntity(resourceBlockId, eventType)
                .addField("startAt", null, b.getStartAt())
                .addField("endAt", null, b.getEndAt())
                .addField("resourceUserId", null, b.getResourceUserId())
                .addField("reason", null, b.getReason()).build();
        AuditLog auditLog = AuditLog.from(ctx, AuditEntityType.RESOURCE_BLOCK, resourceBlockId,
                eventType, DomainEventPayloads.envelopFrom(ctx, resourceBlockId, payloadData));
        auditWriter.append(auditLog);
        return ResourceBlockResponse.from(b);
    }

    @Transactional(readOnly = true)
    public List<ResourceBlockResponse> list(
            RequestContext ctx, UUID resourceUserId, @Nullable OffsetDateTime from, @Nullable OffsetDateTime to) {
        Objects.requireNonNull(ctx, "ctx");
        Objects.requireNonNull(resourceUserId, "resourceUserId");
        resourceBlockAuth.requireList(ctx, resourceUserId);
        return resourceBlockRepo.listInRange(ctx.tenantId(), resourceUserId, from, to).stream().map(
                ResourceBlockResponse::from).toList();
    }

    @Transactional
    public void softDelete(RequestContext ctx, UUID resourceUserId, UUID blockId, @NotBlank String ifMatch) {
        Objects.requireNonNull(ctx, "ctx");
        Objects.requireNonNull(resourceUserId, "resourceUserId");
        Objects.requireNonNull(blockId, "blockId");
        resourceBlockAuth.requireDelete(ctx, resourceUserId);
        ResourceBlock b = resourceBlockRepo.findByIdTenantIdAndIdIdAndResourceUserId(
                ctx.tenantId(), blockId, resourceUserId).orElseThrow(
                () -> new NotFoundException("block: [%s] not found".formatted(blockId)));
        IfMatchPreconditions.require(ifMatch, b.getVersion());
        b.softDelete();
    }

    private static void validateResourceBlockDuration(OffsetDateTime startAt, OffsetDateTime endAt) {
        if(!endAt.isAfter(startAt)) throw new IllegalArgumentException("endAt must be after startAt");
        long minutes = Duration.between(startAt, endAt).toMinutes();
        if(minutes <= 30 || minutes > 24 * 60 * 180)
            throw new IllegalArgumentException("duration should be between 30min and 180 days");
    }
}
