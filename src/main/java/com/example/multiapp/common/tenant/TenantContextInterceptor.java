package com.example.multiapp.common.tenant;

import com.example.multiapp.common.api.ForbiddenException;
import com.example.multiapp.common.web.RequestContexts;
import com.example.multiapp.common.web.RequestIdFilter;
import com.example.multiapp.membership.model.MembershipRole;
import com.example.multiapp.membership.service.TenantMembershipService;
import com.example.multiapp.membership.repo.TenantMembershipRepository;
import com.example.multiapp.user.entity.AppUser;
import com.example.multiapp.user.service.CurrentUserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class TenantContextInterceptor implements HandlerInterceptor {
    private final CurrentUserService currentUserService;
    private final TenantMembershipService tenantMembershipService;
    private final TenantMembershipRepository tenantMembershipRepo;
    private static final String TENANT_HEADER = "X-Tenant-Id";

    @Override
    public boolean preHandle(HttpServletRequest request, @NonNull HttpServletResponse response,
                             @NonNull Object handler) throws Exception {
        String path = request.getRequestURI();
        // 刚注册的用户可能不属于任何租户, 因此/me不依赖RequestContext, 因为RequestContext
        // 中tenantId是必选项
        // 以下路径已经在webConfig对拦截器的应用里排除
        // if(path.startsWith("/actuator") || path.equals("/me")) return true;

        Jwt jwt = extractJwtOrThrow();
        AppUser user = currentUserService.ensureLocalUser(jwt);
        tenantMembershipService.ensurePlatformAdminTenant(user);
        String requestId = (String) request.getAttribute(RequestIdFilter.ATTR);
        String tenantHeader = request.getHeader(TENANT_HEADER);
        if(tenantHeader == null || tenantHeader.isBlank()){
            throw new IllegalArgumentException("Missing X-Tenant-Id");
        }
        final UUID tenantId;
        try {
            tenantId = UUID.fromString(tenantHeader.strip());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid X-Tenant-Id");
        }
        // 平台管理员可以访问任意租户下资源, 不校验tenant_membership表
        MembershipRole role = user.isPlatformAdmin() ? MembershipRole.ADMIN :
                tenantMembershipRepo.findByIdTenantIdAndIdUserId(tenantId, user.getId())
                .orElseThrow(() -> new ForbiddenException("Not a member of tenant")).getRole();
        request.setAttribute(RequestContexts.getATTR(), new RequestContext(tenantId, user.getId(),
                user.isPlatformAdmin(), role, jwt.getIssuer().toString(),
                jwt.getSubject(), requestId));
        return true;
    }

    private Jwt extractJwtOrThrow() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            throw new AccessDeniedException("Unauthenticated");
        }
        Object principal = auth.getPrincipal();
        if(principal instanceof Jwt jwt) return jwt;
        throw new AccessDeniedException("JWT principal not found");
    }
}
