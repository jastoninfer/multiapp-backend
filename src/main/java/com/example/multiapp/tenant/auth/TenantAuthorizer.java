package com.example.multiapp.tenant.auth;

import com.example.multiapp.common.tenant.RequestContext;

public interface TenantAuthorizer {
    void require(RequestContext ctx, TenantAction action, TenantActionPayload payload);
    void require(RequestContext ctx, TenantAction action);
}
