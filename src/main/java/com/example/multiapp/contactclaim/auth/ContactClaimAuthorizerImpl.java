package com.example.multiapp.contactclaim.auth;

import com.example.multiapp.common.tenant.RequestContext;
import com.example.multiapp.membership.model.MembershipRole;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ContactClaimAuthorizerImpl implements ContactClaimAuthorizer{
    @Override
    public void requireIssueClaim(RequestContext ctx, UUID contactId) {
        Objects.requireNonNull(ctx, "ctx");
        Objects.requireNonNull(contactId, "contactId");
        if (ctx.role() == MembershipRole.ADMIN || ctx.role() == MembershipRole.AGENT) {
            return;
        }
        throw new AccessDeniedException("user: [%s] is not authorized to issue a contact claim".
                formatted(ctx.userId()));
    }

    @Override
    public void requireConsumeClaim(RequestContext ctx) {
        Objects.requireNonNull(ctx, "ctx");
    }
}
