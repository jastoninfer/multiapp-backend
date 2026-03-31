package com.example.multiapp.appointment.api;

import com.example.multiapp.appointment.dto.AppointmentDetailResponse;
import com.example.multiapp.appointment.dto.AppointmentQuery;
import com.example.multiapp.appointment.dto.AppointmentSummary;
import com.example.multiapp.appointment.dto.UpdateAppointmentRequest;
import com.example.multiapp.appointment.service.AppointmentService;
import com.example.multiapp.common.api.PageResponse;
import com.example.multiapp.common.tenant.RequestContext;
import com.example.multiapp.common.web.RequestContexts;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;


@Validated
@RestController("/appointments")
@RequestMapping
@RequiredArgsConstructor
public class AppointmentController {
    private final AppointmentService appointmentService;

    @GetMapping
    public PageResponse<AppointmentSummary> list(
            @Valid @NotNull AppointmentQuery query,
            @PageableDefault(size = 20) Pageable pageable,
            HttpServletRequest req) {
        RequestContext ctx = RequestContexts.require(req);
        return PageResponse.from(appointmentService.list(ctx, query, pageable));
    }

    @GetMapping("/{appointmentId}")
    public ResponseEntity<AppointmentDetailResponse> get(
            @PathVariable UUID appointmentId,
            HttpServletRequest req) {
        RequestContext ctx = RequestContexts.require(req);
        AppointmentDetailResponse resp = appointmentService.get(ctx, appointmentId);
        String etag = "\"" + resp.version() + "\"";
        return ResponseEntity.ok().eTag(etag).body(resp);
    }

    @PatchMapping("/{appointmentId}")
    public ResponseEntity<Void> update(
            @PathVariable UUID appointmentId,
            @NotNull @NotBlank
            @Pattern(regexp="^\"\\d+\"$", message="invalid If-Match")
            @RequestHeader(value = "If-Match", required = true)
            String ifMatch,
            @Valid @RequestBody UpdateAppointmentRequest body,
            HttpServletRequest req) {
        RequestContext ctx = RequestContexts.require(req);
        appointmentService.update(ctx, appointmentId, ifMatch, body);
        return ResponseEntity.noContent().build();
    }
}
