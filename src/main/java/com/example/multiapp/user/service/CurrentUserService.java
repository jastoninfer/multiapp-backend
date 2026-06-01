package com.example.multiapp.user.service;

import com.example.multiapp.audit.entity.AuditLog;
import com.example.multiapp.audit.model.AuditEntityType;
import com.example.multiapp.common.aduit.AuditPayloadBuilder;
import com.example.multiapp.common.aduit.AuditWriter;
import com.example.multiapp.common.api.ConflictException;
import com.example.multiapp.common.api.NotFoundException;
import com.example.multiapp.common.event.DomainEventPayloads;
import com.example.multiapp.common.event.DomainEventType;
import com.example.multiapp.common.outbox.DedupKeyFactory;
import com.example.multiapp.common.outbox.OutboxPublisher;
import com.example.multiapp.common.tenant.RequestContext;
import com.example.multiapp.membership.entity.TenantMembership;
import com.example.multiapp.membership.model.MembershipRole;
import com.example.multiapp.membership.repo.TenantMembershipRepository;
import com.example.multiapp.outbox.entity.OutboxEvent;
import com.example.multiapp.user.auth.UserAction;
import com.example.multiapp.user.auth.UserAuthorizer;
import com.example.multiapp.user.dto.MeResponse;
import com.example.multiapp.user.dto.MeResponseWTenants;
import com.example.multiapp.user.dto.MeTenantResponse;
import com.example.multiapp.user.entity.AppUser;
import com.example.multiapp.user.event.UserEventType;
import com.example.multiapp.user.model.UserStatus;
import com.example.multiapp.user.repo.AppUserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectReader;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CurrentUserService {
    private final AppUserRepository userRepo;
    // 对membershipRepo只读
    private final TenantMembershipRepository membershipRepo;
    private final UserAuthorizer userAuth;
    private final AuditWriter auditWriter;
    private final OutboxPublisher outboxPublisher;
    @PersistenceContext
    private EntityManager entityManager;

    @Transactional
    public AppUser ensureLocalUser(Jwt jwt) {
        Objects.requireNonNull(jwt, "jwt");
//        System.out.println(jwt.getClaims());
        String issuer = Objects.requireNonNull(jwt.getIssuer(), "jwt.issuer").toString().strip();
        String sub = Objects.requireNonNull(jwt.getSubject(), "jwt.subject").strip();
        String email = jwt.getClaimAsString("email");
        if(email == null || email.isBlank()) {
            // 这是身份提供方/mapper配置问题, 系统要求email必须存在
            throw new IllegalStateException("JWT missing required claim: email");
        }
        String phone = jwt.getClaimAsString("phone_number");
        String name = firstNonBlank(jwt.getClaimAsString("name"),
                jwt.getClaimAsString("preferred_username"), email);
        String platform_admin = jwt.getClaimAsString("is_platform_admin");
        boolean isPlatformAdmin = platform_admin != null && platform_admin.equals("1");
        return userRepo.findByIssuerAndKeycloakSub(issuer, sub)
                .orElseGet(() -> insertOrGetExisting(issuer, sub, email, name, phone, isPlatformAdmin));
    }

    private AppUser insertOrGetExisting(String issuer, String sub, String email, String name,
                                     String phone, boolean isPlatformAdmin) {
        try {
//            System.out.println("++++++++++++++=");
//            System.out.println("app user insert called....>>>>>");
            AppUser user = AppUser.create(issuer, sub, email, name, phone, isPlatformAdmin);
            AppUser saved = userRepo.saveAndFlush(user);
            entityManager.refresh(saved);
            return saved;
        } catch (DataIntegrityViolationException e) {
            // 并发下另一请求刚插入成功: 重新查
            return userRepo.findByIssuerAndKeycloakSub(issuer, sub)
                    .orElseThrow(() -> e);
        }
    }

    @Transactional(readOnly = true)
    public List<MeTenantResponse> listMyTenants(UUID userId) {
        Objects.requireNonNull(userId, "userId");
        return membershipRepo.findMyTenants(userId);
    }

    @Transactional(readOnly = true)
    public Optional<MeTenantResponse> getMyDefaultTenant(UUID userId) {
        Objects.requireNonNull(userId, "userId");
        return membershipRepo.findMyDefaultTenant(userId);
    }

    @Transactional
    public List<MeTenantResponse> changeMyDefaultTenant(RequestContext ctx, UUID userId, UUID tenantId) {
        Objects.requireNonNull(ctx, "ctx");
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(tenantId, "tenantId");
//        userAuth.require(ctx, UserAction.CHANGE_DEFAULT_TENANT);
        userAuth.requireChangeDefaultTenant(ctx, userId, tenantId);
//        AppUser user = userRepo.findById(userId).orElseThrow(
//                () -> new NotFoundException("user: [%s] not found".formatted(userId)));
        Optional<TenantMembership> prevDefaultMembership = membershipRepo.findDefaultTenant(userId);

//        Optional<MeTenantResponse> prevTenant = membershipRepo.findMyDefaultTenant(userId);
//        if(prevTenant.isPresent() && prevTenant.get().tenantId().equals(tenantId)) {
//            return membershipRepo.findMyTenants(userId);
//        }
        TenantMembership membership =  membershipRepo.findByIdTenantIdAndIdUserId(tenantId, userId).orElseThrow(
                () -> new NotFoundException("tenant: [%s] not found under current user".formatted(tenantId))
        );
        membership.markDefault();
        prevDefaultMembership.ifPresent(TenantMembership::unmarkDefault);
        DomainEventType eventType = UserEventType.USER_DEFAULT_TENANT_CHANGED;
        JsonNode payloadData = AuditPayloadBuilder.forEntity(userId, eventType)
                .addField("defaultTenant",
                        prevDefaultMembership
                                .map(t->t.getId().getTenantId().toString())
                                .orElse("null"),
                        tenantId.toString())
                .build();
        AuditLog auditLog = AuditLog.from(ctx, AuditEntityType.USER, userId, eventType,
                DomainEventPayloads.envelopFrom(ctx, userId, payloadData));
        auditWriter.append(auditLog);
        outboxPublisher.publish(OutboxEvent.from(auditLog, DedupKeyFactory.forRequestScopedUpdate(
                ctx.requestId(), eventType)));
        return membershipRepo.findMyTenants(userId);
    }

    @Transactional(readOnly = true)
    public MeResponseWTenants me(UUID userId) {
        Objects.requireNonNull(userId, "userId");
        AppUser u = userRepo.findById(userId).orElseThrow(() -> new NotFoundException("user"));
//        System.out.println(u.getCreatedAt() == null ? "null" : u.getCreatedAt().toString());
//        System.out.println(u);
        List<MeTenantResponse> tenants = membershipRepo.findMyTenants(userId);
        return new MeResponseWTenants(
                MeResponse.from(u),
                tenants
        );
    }

    @Transactional
    public MeResponse transition(RequestContext ctx, UUID userId, UserStatus toStatus) {
        Objects.requireNonNull(ctx, "ctx");
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(toStatus, "toStatus");
        userAuth.requireTransition(ctx);
//        userAuth.require(ctx, UserAction.CHANGE_STATUS);
        AppUser user = userRepo.findById(userId).orElseThrow(
                () -> new NotFoundException("user: [%s] not found".formatted(userId)));
        UserStatus fromStatus = Objects.requireNonNull(user.getUserStatus(), "status");
        if (fromStatus == toStatus) {
            return MeResponse.from(user);
        }
        // 不能禁用最后一个租户admin
        if (toStatus == UserStatus.DISABLED) {
            long countTenantsWhereUserIsSoleAdmin = membershipRepo.countTenantsWhereUserIsSoleActiveRole(
                    userId, MembershipRole.ADMIN.name(), UserStatus.DISABLED.name());
            if (countTenantsWhereUserIsSoleAdmin > 0) {
                throw new ConflictException("Cannot remove/demote/disable the last ADMIN of the tenant");
            }
        }
        user.transitionTo(toStatus);
        DomainEventType eventType = UserEventType.USER_STATUS_CHANGED;
        JsonNode payloadData = AuditPayloadBuilder.forEntity(userId, eventType)
                .addField("status", fromStatus.name(), toStatus.name()).build();
//        JsonNode payload = DomainEventPayloads.envelop(userId, ctx.userId(),
//                ctx.requestId(), 1, payloadData);
        AuditLog auditLog = AuditLog.from(ctx, AuditEntityType.USER, userId, eventType,
                DomainEventPayloads.envelopFrom(ctx, userId, payloadData));
//        AuditLog auditLog = AuditLog.of(ctx.tenantId(), ctx.userId(), AuditEntityType.USER,
//                userId, eventType, payload, ctx.requestId());
        auditWriter.append(auditLog);
//        String dedupKey = ctx.requestId() + ":" + eventType.key();
        outboxPublisher.publish(OutboxEvent.from(auditLog, DedupKeyFactory.forRequestScopedUpdate(
                ctx.requestId(), eventType)));
        return MeResponse.from(user);
    }

    private String firstNonBlank(String... xs) {
        for(String x: xs) if(x != null && !x.isBlank()) return x;
        return "user";
    }
}
