package com.example.multiapp.membership.service;

import com.example.multiapp.audit.entity.AuditLog;
import com.example.multiapp.audit.model.AuditEntityType;
import com.example.multiapp.common.aduit.AuditPayloadBuilder;
import com.example.multiapp.common.aduit.AuditWriter;
import com.example.multiapp.common.api.*;
import com.example.multiapp.common.crypto.Hashing;
import com.example.multiapp.common.event.DomainEventPayloads;
import com.example.multiapp.common.event.DomainEventType;
import com.example.multiapp.common.outbox.DedupKeyFactory;
import com.example.multiapp.common.outbox.OutboxPublisher;
import com.example.multiapp.common.tenant.RequestContext;
import com.example.multiapp.common.user.UserReader;
import com.example.multiapp.common.web.IfMatchPreconditions;
import com.example.multiapp.idempotency.service.IdempotencyService;
import com.example.multiapp.membership.auth.MembershipAction;
import com.example.multiapp.membership.auth.MembershipAuthorizer;
import com.example.multiapp.membership.dto.CreateMemberRequest;
import com.example.multiapp.membership.dto.MemberUserInfo;
import com.example.multiapp.membership.dto.MembershipCreatedResponse;
import com.example.multiapp.membership.dto.UpdateMemberRequest;
import com.example.multiapp.membership.entity.TenantMembership;
import com.example.multiapp.membership.event.MembershipEventType;
import com.example.multiapp.membership.model.MembershipRole;
import com.example.multiapp.membership.repo.TenantMembershipRepository;
import com.example.multiapp.outbox.entity.OutboxEvent;
import com.example.multiapp.tenant.repo.TenantRepository;
import com.example.multiapp.user.entity.AppUser;
import com.example.multiapp.user.model.UserStatus;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.core.env.Environment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
@RequiredArgsConstructor
public class TenantMembershipService {
    private final TenantMembershipRepository membershipRepo;
    private final TenantRepository tenantRepo;
    private final MembershipAuthorizer memberAuth;
    private final IdempotencyService idemService;
//    private final String PLATFORM_ADMIN_TENANT = "_platform_admin";
    private final Environment env;
    private final UserReader userReader;
    private final AuditWriter auditWriter;
    private final OutboxPublisher outboxPublisher;
    @Transactional
    public void ensurePlatformAdminTenant(AppUser user) {
        Objects.requireNonNull(user, "user");
        if(!user.isPlatformAdmin()) return;
        UUID tenantId = tenantRepo
                .findByNameCi(
                        env.getProperty("app.platform.admin.tenant","__platform_admin"))
                .orElseThrow(() -> new IllegalStateException("platform admin tenant doesn't exist"))
                .getId();
        UUID userId = user.getId();
//        if (!membershipRepo.existsByIdTenantIdAndIdUserId(tenantId, userId)) {
//            // 还是要考虑并发
//
//            membershipRepo.save(TenantMembership.create(tenantId, userId,
//                    MembershipRole.ADMIN, false));
//        }
        membershipRepo.insertIgnore(tenantId, userId, MembershipRole.ADMIN.name());
    }

    // 列出当前租户成员, q 可以匹配user的displayName/email
    @Transactional(readOnly = true)
    public Page<MemberUserInfo> listMembers(RequestContext ctx, @Nullable MembershipRole role, String q, Pageable pageable) {
        Objects.requireNonNull(ctx, "ctx");
//        memberAuth.require(ctx, MembershipAction.LIST);
        memberAuth.requireList(ctx);
        // 首先尝试将role匹配到某个Role(Enum)
//        String roleName = role == null ? null : role.name();
        // 权限控制, 只有tenant ADMIN + platform ADMIN才有权限做这个查询
        Pageable p = PageNormalizer.normalize(pageable, 100, 20,
                Sort.by(Sort.Order.desc("isDefault"), Sort.Order.desc("createdAt"),
                        Sort.Order.asc("id.userId")),
                Set.of("isDefault", "createdAt", "id.userId"));
        Page<MemberUserInfo> page;
        if(q == null || q.isBlank()) {
            page = membershipRepo.listMembers(ctx.tenantId(), role, p);
        } else {
            String like = "%" + q.strip().toLowerCase(Locale.ROOT) + "%";
            page = membershipRepo.searchMembers(ctx.tenantId(), role, like, p);
        }
        return page;
    }

    @Transactional(readOnly = true)
    public MemberUserInfo getMember(RequestContext ctx, UUID userId) {
        Objects.requireNonNull(ctx, "ctx");
        Objects.requireNonNull(userId, "userId");
        memberAuth.require(ctx, MembershipAction.READ, userId, UUID.class);
        return membershipRepo.findMember(ctx.tenantId(), userId).orElseThrow(
                () -> new NotFoundException("user: [%s] under tenant: [%s] not found".
                        formatted(userId, ctx.tenantId())));
    }

    // 增加成员, 先置条件是userId已经存在于app_user表
    @Transactional
    public MembershipCreatedResponse addMember(RequestContext ctx, @NotBlank String idemKey,
                                        @NotNull CreateMemberRequest req) {
        Objects.requireNonNull(ctx, "ctx");
        Objects.requireNonNull(idemKey, "Idempotency-Key");
        Objects.requireNonNull(req, "req");
        Objects.requireNonNull(req.userId(), "req.userId");
        UUID userId = req.userId();
        UUID tenantId = ctx.tenantId();
        if(!userReader.existsUser(userId)) {
            throw new NotFoundException("user with id: [%s] not found".formatted(userId));
        }
        memberAuth.require(ctx, MembershipAction.CREATE);
        final String requestHash = Hashing.sha256Hex("POST:/members\n" + req.toStableString());
        // 插入失败, 但有缓存才有返回值
        Optional<MembershipCreatedResponse> cached = idemService.tryInsert(tenantId, ctx.userId(), idemKey,
                requestHash, MembershipCreatedResponse.class);
        if(cached.isPresent()) return cached.get();
        DomainEventType  eventType = MembershipEventType.MEMBERSHIP_CREATED;
        // 由于membership是(tenant_id, user_id)作为主键, 这里写audit_log只考虑将user_id作为entity_id,
        // tenant信息写入payload中
        UUID entityId = userId;
        JsonNode payloadData = AuditPayloadBuilder.forEntity(entityId, eventType)
                .addField("tenantId", "null", tenantId.toString())
                .addField("role", "null", req.role().name())
                .addField("isDefault", "null", String.valueOf(req.isDefault())).build();
//        JsonNode payload = DomainEventPayloads.envelop(entityId, ctx.userId(), ctx.requestId(),
//                1, payloadData);
        // 由于暂未开启应用层审计(@EnableJpaAuditing + @CreatedDate/@LastModifiedDate),
        // 因此这里save返回的membership可能依旧是没有created_at, updated_at
        // 先忽略req.isDefault()参数必然插入失败, 如果要更改default租户, 可交给后续
        TenantMembership membership =  membershipRepo.save(TenantMembership.create(
                tenantId, userId, req.role(), false));
        AuditLog auditLog = AuditLog.from(ctx, AuditEntityType.MEMBERSHIP, entityId, eventType,
                DomainEventPayloads.envelopFrom(ctx, entityId, payloadData));
//        AuditLog auditLog = AuditLog.of(tenantId, ctx.userId(), AuditEntityType.MEMBERSHIP, entityId,
//                eventType, payload, ctx.requestId());
        auditWriter.append(auditLog);
//        String dedupKey = idemKey + ":" + eventType.key();
        outboxPublisher.publish(OutboxEvent.from(auditLog, DedupKeyFactory.forCreate(idemKey, eventType)));
        MembershipCreatedResponse resp = MembershipCreatedResponse.from(membership);
        idemService.tryComplete(tenantId, ctx.userId(), idemKey, requestHash, resp);
        return resp;
    }

    // 目前为止更新的内容只有role
    @Transactional
    public void update(RequestContext ctx, UUID userId, @NotBlank String ifMatch,
                                     UpdateMemberRequest req) {
        Objects.requireNonNull(ctx, "ctx");
        Objects.requireNonNull(userId, "userId");
        UUID tenantId = ctx.tenantId();
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(req, "req");
        memberAuth.require(ctx, MembershipAction.UPDATE, userId, UUID.class);
        // 不能修改自己
        if(ctx.userId().equals(userId)) {
            throw new IllegalStateException("cannot update yourself's role");
        }
        TenantMembership membership = membershipRepo.findByIdTenantIdAndIdUserId(tenantId, userId)
                .orElseThrow(() -> new NotFoundException("user not found"));
        IfMatchPreconditions.require(ifMatch, membership.getVersion());
        // 注意业务规则, 禁止移除/降级最后一个租户管理员(一个租户管理员不能从>0变为0)
        MembershipRole fromRole = membership.getRole();
        MembershipRole targetRole = req.targetRole();
        // 使用userId作为替代entityId
        DomainEventType eventType = MembershipEventType.MEMBERSHIP_UPDATED;
        AuditPayloadBuilder builder = AuditPayloadBuilder.forEntity(userId, eventType);
        boolean updated = false;
        if (!Objects.isNull(targetRole) && fromRole != targetRole) {
            if (fromRole == MembershipRole.ADMIN) {
                // 降级
                long activeAdmins = membershipRepo.countActiveMembersByRole(tenantId,
                        MembershipRole.ADMIN.name(), UserStatus.DISABLED.name());
                if (activeAdmins == 1) {
                   throw new ConflictException("Cannot remove/demote/disable the last ADMIN of the tenant");
                }
            }
            // 尝试改变角色
            membership.changeRole(targetRole);
            builder.addField("role", fromRole.name(), targetRole.name());
            updated = true;
        }
        if(!updated) return;
//        JsonNode payload =  DomainEventPayloads.envelop(userId, ctx.userId(), ctx.requestId(), 1,
//                builder.build());
        AuditLog auditLog = AuditLog.from(ctx, AuditEntityType.MEMBERSHIP, userId, eventType,
                DomainEventPayloads.envelopFrom(ctx, userId, builder.build()));
//        AuditLog auditLog = AuditLog.of(tenantId, ctx.userId(), AuditEntityType.MEMBERSHIP, userId,
//                eventType, payloadData, ctx.requestId());
        auditWriter.append(auditLog);
//        String dedupKey = ctx.requestId() + ":" + eventType.key();
        outboxPublisher.publish(OutboxEvent.from(auditLog, DedupKeyFactory.forRequestScopedUpdate(
                ctx.requestId(), eventType)));
//        return MembershipCreatedResponse.from(membership);
    }

    @Transactional
    public void delete (RequestContext ctx, UUID userId, @NotBlank String ifMatch) {
        Objects.requireNonNull(ctx, "ctx");
        Objects.requireNonNull(userId, "userId");
        UUID tenantId = ctx.tenantId();
        Objects.requireNonNull(tenantId, "tenantId");
        memberAuth.require(ctx, MembershipAction.DELETE, userId, UUID.class);
        TenantMembership membership = membershipRepo.findByIdTenantIdAndIdUserId(tenantId, userId)
                .orElseThrow(() -> new NotFoundException("user not found"));
        IfMatchPreconditions.require(ifMatch, membership.getVersion());
        // 注意业务规则, 禁止移除最后一个租户管理员(一个租户管理员不能从>0变为0)
        // 不能删除你自己
        if(ctx.userId().equals(userId)) {
            throw new IllegalArgumentException("cannot delete yourself's role");
        }
        long activeAdmins = membershipRepo.countActiveMembersByRole(tenantId,
                MembershipRole.ADMIN.name(), UserStatus.DISABLED.name());
        if (activeAdmins == 1) {
            throw new ConflictException("Cannot remove/demote/disable the last ADMIN of the tenant");
        }
        if(true) {
            throw new IllegalStateException("member deletion function is not open yet");
        }
        membershipRepo.delete(membership);
        DomainEventType eventType = MembershipEventType.MEMBERSHIP_DELETED;
        AuditPayloadBuilder builder = AuditPayloadBuilder.forEntity(userId, eventType);
//        JsonNode payload =  DomainEventPayloads.envelop(userId, ctx.userId(), ctx.requestId(), 1,
//                builder.build());
        AuditLog auditLog = AuditLog.from(ctx, AuditEntityType.MEMBERSHIP, userId, eventType,
                DomainEventPayloads.envelopFrom(ctx, userId, builder.build()));
        auditWriter.append(auditLog);
        outboxPublisher.publish(OutboxEvent.from(auditLog, DedupKeyFactory.forRequestScopedUpdate(
                ctx.requestId(), eventType)));
    }
}
