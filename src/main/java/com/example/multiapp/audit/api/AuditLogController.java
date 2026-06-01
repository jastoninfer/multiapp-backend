package com.example.multiapp.audit.api;

import com.example.multiapp.audit.dto.AuditLogQuery;
import com.example.multiapp.audit.dto.AuditLogResponse;
import com.example.multiapp.audit.service.AuditLogService;
import com.example.multiapp.common.api.PageResponse;
import com.example.multiapp.common.tenant.RequestContext;
import com.example.multiapp.common.web.RequestContexts;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/audit-logs")
public class AuditLogController {
    private final AuditLogService auditLogService;

    @GetMapping
    public PageResponse<AuditLogResponse> list(
            @Valid @NotNull AuditLogQuery query,
            @PageableDefault(size = 20) Pageable pageable,
            HttpServletRequest req
    ) {
        RequestContext ctx = RequestContexts.require(req);
        return PageResponse.from(auditLogService.list(ctx, query, pageable));
    }
}
