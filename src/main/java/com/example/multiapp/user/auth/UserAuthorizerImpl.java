package com.example.multiapp.user.auth;

import com.example.multiapp.common.tenant.RequestContext;
import com.example.multiapp.common.api.ForbiddenException;
import org.springframework.stereotype.Service;

import java.util.Objects;
import java.util.UUID;

@Service
public class UserAuthorizerImpl implements UserAuthorizer{
//    public void require(RequestContext ctx, UserAction action) {
//        Objects.requireNonNull(ctx, "ctx");
//        Objects.requireNonNull(action, "action");
//        switch (action) {
//            case CHANGE_STATUS -> {
//                if(ctx.isPlatformAdmin()) return;
//            }
//            case CHANGE_DEFAULT_TENANT -> {
//                // 只有本人或超级管理员可以修改
//                if(ctx.isPlatformAdmin() || )
//            }
//            default -> {
//                throw new IllegalArgumentException("Unhandled action: " + action);
//            }
//        }
//        throw new ForbiddenException("Access denied: action=%s".formatted(action));
//    }

    @Override
    public void requireTransition(RequestContext ctx) {
        Objects.requireNonNull(ctx, "ctx");
        if(ctx.isPlatformAdmin()) return;
        throw new ForbiddenException("Access denied: Transition");
    }

    @Override
    public void requireChangeDefaultTenant(RequestContext ctx, UUID userId, UUID targetTenantId) {
        Objects.requireNonNull(ctx, "ctx");
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(targetTenantId, "targetTenantId");
        if(ctx.isPlatformAdmin() || ctx.userId().equals(userId)) return;
        throw new ForbiddenException("Access denied: ChangeDefaultTenant");
    }
}
