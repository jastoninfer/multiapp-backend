package com.example.multiapp.resource.api;

import com.example.multiapp.common.tenant.RequestContext;
import com.example.multiapp.common.web.RequestContexts;
import com.example.multiapp.resource.dto.AvailabilityResponse;
import com.example.multiapp.resource.dto.CreateWorkingHoursRequest;
import com.example.multiapp.resource.service.AvailabilityService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.util.UUID;

@Validated
@RestController
@RequestMapping("/resources/{resourceUserId}/availability")
@RequiredArgsConstructor
public class AvailabilityController {
    private final AvailabilityService availabilityService;
    @GetMapping
    public ResponseEntity<AvailabilityResponse> get(
            @PathVariable UUID resourceUserId,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            OffsetDateTime from,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            OffsetDateTime to,
            HttpServletRequest req) {
        RequestContext ctx = RequestContexts.require(req);
        AvailabilityResponse resp = availabilityService.getAvailability(ctx, resourceUserId, from, to);
        return ResponseEntity.ok().body(resp);
    }

    @PutMapping
    public ResponseEntity<Void> update(
        @PathVariable UUID resourceUserId,
        @Valid @RequestBody CreateWorkingHoursRequest createWorkingHoursRequest,
        HttpServletRequest req) {
        RequestContext ctx = RequestContexts.require(req);
        availabilityService.putWorkingHours(ctx, resourceUserId, createWorkingHoursRequest);
        return ResponseEntity.noContent().build();
    }
}
