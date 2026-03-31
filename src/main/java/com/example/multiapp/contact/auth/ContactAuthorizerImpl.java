package com.example.multiapp.contact.auth;

import com.example.multiapp.common.tenant.RequestContext;
import com.example.multiapp.membership.model.MembershipRole;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.util.Objects;

@Service
@RequiredArgsConstructor
public class ContactAuthorizerImpl implements ContactAuthorizer{
    @Override
    public void requireCreate(RequestContext ctx) {
        Objects.requireNonNull(ctx, "ctx");
        if(ctx.role() != MembershipRole.ADMIN && ctx.role() != MembershipRole.AGENT) {
            throw new AccessDeniedException("only admin|agent can add contact");
        }
    }

    @Override
    public void requireList(RequestContext ctx) {
        Objects.requireNonNull(ctx, "ctx");
        if(ctx.role() != MembershipRole.ADMIN && ctx.role() != MembershipRole.AGENT) {
            throw new AccessDeniedException("only admin|agent can list contacts");
        }
    }

    @Override
    public void requireRead(RequestContext ctx) {
        Objects.requireNonNull(ctx, "ctx");
        if(ctx.role() != MembershipRole.ADMIN && ctx.role() != MembershipRole.AGENT) {
            throw new AccessDeniedException("only admin|agent can read contact details");
        }
    }

    @Override
    public void requireUpdate(RequestContext ctx) {
        Objects.requireNonNull(ctx, "ctx");
        if(ctx.role() != MembershipRole.ADMIN && ctx.role() != MembershipRole.AGENT) {
            throw new AccessDeniedException("only admin|agent can update a contact");
        }
    }
}
