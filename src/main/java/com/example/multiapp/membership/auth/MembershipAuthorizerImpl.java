package com.example.multiapp.membership.auth;

import com.example.multiapp.common.api.ForbiddenException;
import com.example.multiapp.common.tenant.RequestContext;
import com.example.multiapp.membership.model.MembershipRole;
import com.example.multiapp.membership.repo.TenantMembershipRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MembershipAuthorizerImpl implements MembershipAuthorizer{
    private final TenantMembershipRepository membershipRepo;

    // LIST, CREATE
    @Override
    public void require(RequestContext ctx, MembershipAction action) {
        Objects.requireNonNull(ctx, "ctx");
        Objects.requireNonNull(action, "action");
        if(ctx.role() == MembershipRole.ADMIN) return;
        throw new ForbiddenException("Access denied: action=%s".formatted(action));
    }

    @Override
    public void requireList(RequestContext ctx) {
        Objects.requireNonNull(ctx, "ctx");
        if(ctx.role() == MembershipRole.ADMIN ||
            ctx.role() == MembershipRole.AGENT) return;
        throw new ForbiddenException("Access denied: list members");
    }

    // READ, UPDATE, DELETE
    // 注意这里的payload是某个(租户下)具体成员, 因此期望的T是UUID
    @Override
    public <T> void require(RequestContext ctx, MembershipAction action, T payload, Class<T> classType) {
        Objects.requireNonNull(ctx, "ctx");
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(payload, "payload");
        try {
            UUID userId = (UUID) payload;
            if(ctx.role() == MembershipRole.ADMIN) return;
            switch (action) {
                case UPDATE, DELETE -> {
                    throw new ForbiddenException("Access denied: action=%s".formatted(action));
                }
                case READ -> {
                    if(ctx.role() != MembershipRole.AGENT && !userId.equals(ctx.userId()))
                        throw new ForbiddenException("Access denied: action=%s".formatted(action));
                }
                default -> throw new IllegalArgumentException("Unhandled action: " + action);
            }
        } catch (ClassCastException e) {
            throw new IllegalArgumentException("anticipated payload type: [%s], got: [%s]".formatted(
                    classType.getName(), payload.getClass().getName()), e);
        }
    }


//    private final boolean isTenantAdmin(RequestContext ctx) {
//        return membershipRepo.findByIdTenantIdAndIdUserId(ctx.tenantId(), ctx.userId())
//                .orElseThrow(() -> new ForbiddenException("Not a member of tenant")).getRole().equals(MembershipRole.ADMIN);
//    }
}
