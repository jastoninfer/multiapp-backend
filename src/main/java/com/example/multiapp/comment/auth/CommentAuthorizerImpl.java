package com.example.multiapp.comment.auth;

import com.example.multiapp.comment.dto.CreateCommentRequest;
import com.example.multiapp.comment.model.CommentVisibility;
import com.example.multiapp.common.tenant.RequestContext;
import com.example.multiapp.membership.model.MembershipRole;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.util.Objects;

@Service
@RequiredArgsConstructor
public class CommentAuthorizerImpl implements CommentAuthorizer{
    @Override
    public void requirePost(RequestContext ctx, CreateCommentRequest req) {
        Objects.requireNonNull(ctx, "ctx");
        Objects.requireNonNull(req, "req");
        if(req.visibility() == CommentVisibility.INTERNAL) {
            if(ctx.role() != MembershipRole.ADMIN && ctx.role() != MembershipRole.AGENT) {
                throw new AccessDeniedException("only admin/agent can post internal comment");
            }
        }
    }

    @Override
    public void requireList(RequestContext ctx) {
        Objects.requireNonNull(ctx, "ctx");
    }
}
