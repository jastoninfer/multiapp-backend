package com.example.multiapp.tenant.api;

import com.example.multiapp.common.api.PageResponse;
import com.example.multiapp.common.tenant.RequestContext;
import com.example.multiapp.common.web.RequestContexts;
import com.example.multiapp.tenant.dto.CreateTenantRequest;
import com.example.multiapp.tenant.dto.TenantResponse;
import com.example.multiapp.tenant.dto.TenantTransitionRequest;
import com.example.multiapp.tenant.dto.UpdateTenantRequest;
import com.example.multiapp.tenant.service.TenantService;
import com.example.multiapp.ticket.dto.TicketTransitionRequest;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Validated
@RestController
@RequiredArgsConstructor
public class TenantController {
    private final TenantService tenantService;

    @GetMapping("/tenant")
    public TenantResponse get(HttpServletRequest req) {
        RequestContext ctx = RequestContexts.require(req);
        return tenantService.getById(ctx);
    }

    @GetMapping("/admin/tenants")
    public PageResponse<TenantResponse> list(
            @PageableDefault(size=20) Pageable pageable,
            HttpServletRequest req) {
        RequestContext ctx = RequestContexts.require(req);
        return PageResponse.from(tenantService.list(ctx, pageable));
    }

    @PostMapping("/admin/tenants")
    public ResponseEntity<TenantResponse> create(
            @NotBlank
            @RequestHeader(value = "Idempotency-Key", required = true) String idemKey,
            @Valid @RequestBody CreateTenantRequest body,
            HttpServletRequest req
    ) {
        RequestContext ctx = RequestContexts.require(req);
        TenantResponse resp = tenantService.create(ctx, idemKey, body);
        return ResponseEntity.status(HttpStatus.CREATED).body(resp);
    }

    @PostMapping("/tenant/transition")
    public TenantResponse transition(
            @Valid @RequestBody TenantTransitionRequest body,
            HttpServletRequest req
    ) {
        RequestContext ctx = RequestContexts.require(req);
        return tenantService.transition(ctx, body.toStatus());
    }

    @PatchMapping("/tenant")
    public TenantResponse patch(
            @Valid @RequestBody UpdateTenantRequest body,
            HttpServletRequest req
    ) {
        RequestContext ctx = RequestContexts.require(req);
        return tenantService.update(ctx, body);
    }
}
