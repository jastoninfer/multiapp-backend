package com.example.multiapp.comment.auth;

import com.example.multiapp.comment.dto.CreateCommentRequest;
import com.example.multiapp.common.tenant.RequestContext;

public interface CommentAuthorizer {
    void requirePost(RequestContext ctx, CreateCommentRequest req);
    void requireList(RequestContext ctx);
}
