package com.example.multiapp.user.auth;

import com.example.multiapp.common.tenant.RequestContext;

public interface UserAuthorizer {
    void require(RequestContext ctx, UserAction action);
}
