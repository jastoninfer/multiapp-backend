package com.example.multiapp.user.api;

import com.example.multiapp.common.api.NotFoundException;
import com.example.multiapp.common.tenant.RequestContext;
import com.example.multiapp.common.web.RequestContexts;
import com.example.multiapp.membership.repo.TenantMembershipRepository;
import com.example.multiapp.user.auth.UserAuthorizer;
import com.example.multiapp.user.dto.MeResponse;
import com.example.multiapp.user.dto.MeResponseWTenants;
import com.example.multiapp.user.dto.MeTenantResponse;
import com.example.multiapp.user.dto.UserTransitionRequest;
import com.example.multiapp.user.entity.AppUser;
import com.example.multiapp.user.repo.AppUserRepository;
import com.example.multiapp.user.service.CurrentUserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Validated
@RestController
@RequestMapping
@RequiredArgsConstructor
public class UserController {
    private final CurrentUserService userService;

    @GetMapping("/me")
    public MeResponseWTenants me(@AuthenticationPrincipal Jwt jwt) {
        AppUser user = userService.ensureLocalUser(jwt);
        UUID userId = user.getId();
        return userService.me(userId);
    }

    @GetMapping("/me/tenants")
    public List<MeTenantResponse> myTenants(@AuthenticationPrincipal Jwt jwt) {
        AppUser user = userService.ensureLocalUser(jwt);
        UUID userId = user.getId();
        return userService.listMyTenants(userId);
    }

    @GetMapping("/me/default-tenant")
    public MeTenantResponse myDefaultTenant(@AuthenticationPrincipal Jwt jwt) {
        AppUser user = userService.ensureLocalUser(jwt);
        UUID userId = user.getId();
        return userService.getMyDefaultTenant(userId).orElseThrow(
                () -> new NotFoundException("default tenant not found"));
    }

    @PostMapping("/users/{userId}/transition")
    public MeResponse transition(
            @PathVariable UUID userId,
            @Valid @RequestBody UserTransitionRequest body,
            HttpServletRequest req
    ) {
        RequestContext ctx = RequestContexts.require(req);
        return userService.transition(ctx, userId, body.toStatus());
    }
}
