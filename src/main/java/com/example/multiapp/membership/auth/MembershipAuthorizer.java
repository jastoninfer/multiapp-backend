package com.example.multiapp.membership.auth;

import com.example.multiapp.common.tenant.RequestContext;

public interface MembershipAuthorizer {
    void requireList(RequestContext ctx);
    void require(RequestContext ctx, MembershipAction action);
    <T> void require(RequestContext ctx, MembershipAction action, T payload, Class<T> classType);
}
