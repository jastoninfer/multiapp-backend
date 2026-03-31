package com.example.multiapp.resource.auth;

import com.example.multiapp.common.tenant.RequestContext;
import com.example.multiapp.membership.model.MembershipRole;
import com.example.multiapp.membership.repo.TenantMembershipRepository;
import com.example.multiapp.resource.dto.CreateResourceBlockRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ResourceBlockAuthorizerImpl implements ResourceBlockAuthorizer{
    private final TenantMembershipRepository membershipRepo;
    @Override
    public void requireCreate(RequestContext ctx, UUID resourceUserId, CreateResourceBlockRequest req) {
        Objects.requireNonNull(ctx, "ctx");
        Objects.requireNonNull(resourceUserId, "resourceUserId");
        Objects.requireNonNull(req, "CreateResourceBlockRequest");
        // 判定resourceUserId确实是当前tenant下role为RESOURCE_USER的用户
        if(!membershipRepo.existsByIdTenantIdAndIdUserIdAndRole(ctx.tenantId(), resourceUserId,
                MembershipRole.RESOURCE_USER)) {
            throw new IllegalArgumentException("resource user id given is not valid");
        }
        if(ctx.role() == MembershipRole.RESOURCE_USER) {
            if(!ctx.userId().equals(resourceUserId)) {
                throw new AccessDeniedException("resource user can create only his own resource block");
            }
        } else if(ctx.role() != MembershipRole.ADMIN && ctx.role() != MembershipRole.AGENT) {
            throw new AccessDeniedException("only admin/agent/resource user can create resource block");
        }
    }

    @Override
    public void requireList(RequestContext ctx, UUID resourceUserId) {
        Objects.requireNonNull(ctx, "ctx");
        Objects.requireNonNull(resourceUserId, "resourceUserId");
        if(ctx.role() == MembershipRole.RESOURCE_USER) {
            if(!ctx.userId().equals(resourceUserId)) {
                throw new AccessDeniedException("resource user can view only his own resource blocks");
            }
        } else if(ctx.role() != MembershipRole.ADMIN && ctx.role() != MembershipRole.AGENT) {
            throw new AccessDeniedException("only admin/agent/resource user can view resource blocks");
        }
    }

    @Override
    public void requireDelete(RequestContext ctx, UUID resourceUserId) {
        Objects.requireNonNull(ctx, "ctx");
        Objects.requireNonNull(resourceUserId, "resourceUserId");
        if(ctx.role() == MembershipRole.RESOURCE_USER) {
            if(!ctx.userId().equals(resourceUserId)) {
                throw new AccessDeniedException("resource user can view delete his own resource blocks");
            }
        } else if(ctx.role() != MembershipRole.ADMIN && ctx.role() != MembershipRole.AGENT) {
            throw new AccessDeniedException("only admin/agent/resource user can delete resource blocks");
        }
    }
}
