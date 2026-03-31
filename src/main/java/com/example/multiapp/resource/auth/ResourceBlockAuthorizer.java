package com.example.multiapp.resource.auth;

import com.example.multiapp.common.tenant.RequestContext;
import com.example.multiapp.resource.dto.CreateResourceBlockRequest;
import com.example.multiapp.resource.dto.ResourceBlockResponse;

import java.util.UUID;

public interface ResourceBlockAuthorizer {
    void requireCreate(RequestContext ctx, UUID resourceUserId, CreateResourceBlockRequest req);
    void requireList(RequestContext ctx, UUID resourceUserId);
    void requireDelete(RequestContext ctx, UUID resourceUserId);
}
