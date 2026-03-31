package com.example.multiapp.contactclaim.auth;

import com.example.multiapp.common.tenant.RequestContext;

import java.util.UUID;

public interface ContactClaimAuthorizer {
    void requireIssueClaim(RequestContext ctx, UUID contactId);
    void requireConsumeClaim(RequestContext ctx);
}
