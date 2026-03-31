package com.example.multiapp.membership.api;

import com.example.multiapp.common.api.PageResponse;
import com.example.multiapp.common.tenant.RequestContext;
import com.example.multiapp.common.web.RequestContexts;
import com.example.multiapp.membership.dto.CreateMemberRequest;
import com.example.multiapp.membership.dto.MemberUserInfo;
import com.example.multiapp.membership.dto.MembershipCreatedResponse;
import com.example.multiapp.membership.dto.UpdateMemberRequest;
import com.example.multiapp.membership.service.TenantMembershipService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
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
@RequestMapping("/members")
public class TenantMembershipController {
    private final TenantMembershipService membershipService;

    @GetMapping
    public PageResponse<MemberUserInfo> listMembers(
            @Size(max=200)
            @RequestParam(required = false) String q,
            @PageableDefault(value = 20) Pageable pageable,
            HttpServletRequest req) {
        RequestContext ctx = RequestContexts.require(req);
        return PageResponse.from(membershipService.listMembers(ctx, q, pageable));
    }

    @GetMapping("/{userId}")
    public ResponseEntity<MemberUserInfo> getMember(@PathVariable UUID userId, HttpServletRequest req) {
        RequestContext ctx = RequestContexts.require(req);
        MemberUserInfo resp = membershipService.getMember(ctx, userId);
        String etag = "\"" + resp.version() + "\"";
        return ResponseEntity.status(HttpStatus.OK).eTag(etag).body(resp);
    }

    @PostMapping
    public ResponseEntity<MembershipCreatedResponse> addMember(
            @NotBlank
            @RequestHeader(value = "Idempotency-Key", required = true) String idemKey,
            @Valid @RequestBody CreateMemberRequest  body,
            HttpServletRequest req) {
        RequestContext ctx = RequestContexts.require(req);
        MembershipCreatedResponse resp = membershipService.addMember(ctx, idemKey, body);
        String etag = "\"" + resp.version() + "\"";
        return ResponseEntity.status(HttpStatus.CREATED).eTag(etag).body(resp);
    }

    @PatchMapping("/{userId}")
    public ResponseEntity<Void> updateMember(
            @PathVariable UUID userId,
            @NotNull @NotBlank
            @Pattern(regexp="^\"\\d+\"$", message="invalid If-Match")
            @RequestHeader(value = "If-Match", required = true)
            String ifMatch,
            @Valid @RequestBody UpdateMemberRequest body,
            HttpServletRequest req) {
        RequestContext ctx = RequestContexts.require(req);
        membershipService.update(ctx, userId, ifMatch, body);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{userId}")
    public ResponseEntity<Void> deleteMember(
            @PathVariable UUID userId,
            @NotNull @NotBlank
            @Pattern(regexp="^\"\\d+\"$", message="invalid If-Match")
            @RequestHeader(value = "If-Match", required = true)
            String ifMatch,
            HttpServletRequest req
    ) {
        RequestContext ctx = RequestContexts.require(req);
        membershipService.delete(ctx, userId, ifMatch);
        return ResponseEntity.noContent().build();
    }
}
