package com.example.multiapp.tenant.auth;

import com.example.multiapp.common.tenant.RequestContext;
import com.example.multiapp.membership.model.MembershipRole;
import com.example.multiapp.membership.repo.TenantMembershipRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

@Service
@RequiredArgsConstructor
public class TenantAuthorizerImpl implements TenantAuthorizer{
    private final TenantMembershipRepository membershipRepo;

    // CREATE, LIST, CHANGE_STATUS
    @Override public void require(RequestContext ctx, TenantAction action) {
        Objects.requireNonNull(ctx, "ctx");
        Objects.requireNonNull(action, "action");
        switch (action) {
            case CREATE, LIST, CHANGE_STATUS -> {
                if(!ctx.isPlatformAdmin())
                    throw new AccessDeniedException("Access denied: action=%s".formatted(action));
            }
            default -> throw new IllegalArgumentException("Unhandled action: " + action);
        }
    }

    // READ, UPDATE
    @Transactional(readOnly = true)
    @Override public void require(RequestContext ctx, TenantAction action, TenantActionPayload payload) {
        Objects.requireNonNull(ctx, "ctx");
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(payload, "payload");
        switch (action) {
            case READ -> {
                if(ctx.isPlatformAdmin() || membershipRepo.
                        existsByIdTenantIdAndIdUserId(payload.tenantId(), ctx.userId())) return;
            }
            case UPDATE -> {
                if(ctx.isPlatformAdmin() || membershipRepo.
                        existsByIdTenantIdAndIdUserIdAndRole(payload.tenantId(), ctx.userId(), MembershipRole.ADMIN))
                    return;
            }
            default -> throw new IllegalArgumentException("Unhandled action: " + action);
        }
        throw new AccessDeniedException("Access denied: action=%s tenantId=%s".
                formatted(action, payload.tenantId()));
    }
}
