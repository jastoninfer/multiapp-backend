package com.example.multiapp.user.auth;

import com.example.multiapp.common.tenant.RequestContext;

import java.util.UUID;

public interface UserAuthorizer {
//    void require(RequestContext ctx, UserAction action);
    void requireTransition(RequestContext ctx);
    void requireChangeDefaultTenant(RequestContext ctx, UUID userId, UUID targetTenantId);
}
