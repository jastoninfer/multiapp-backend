package com.example.multiapp.user.auth;

import com.example.multiapp.common.tenant.RequestContext;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.util.Objects;

@Service
public class UserAuthorizerImpl implements UserAuthorizer{
    @Override
    public void require(RequestContext ctx, UserAction action) {
        Objects.requireNonNull(ctx, "ctx");
        Objects.requireNonNull(action, "action");
        switch (action) {
            case CHANGE_STATUS -> {
                if(ctx.isPlatformAdmin()) return;
            }
            default -> {
                throw new IllegalArgumentException("Unhandled action: " + action);
            }
        }
        throw new AccessDeniedException("Access denied: action=%s".formatted(action));
    }
}
