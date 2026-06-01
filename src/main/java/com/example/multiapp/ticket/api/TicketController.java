package com.example.multiapp.ticket.api;

import com.example.multiapp.appointment.dto.AppointmentCreatedResponse;
import com.example.multiapp.appointment.dto.CreateAppointmentRequest;
import com.example.multiapp.appointment.service.AppointmentService;
import com.example.multiapp.common.api.PageResponse;
import com.example.multiapp.common.tenant.RequestContext;
import com.example.multiapp.common.web.RequestContexts;
import com.example.multiapp.ticket.dto.*;
import com.example.multiapp.ticket.service.TicketService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.micrometer.observation.autoconfigure.ObservationProperties;
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
@RequestMapping("/tickets")
@RequiredArgsConstructor
public class TicketController {
    private final TicketService ticketService;
    private final AppointmentService appointmentService;

    @PostMapping("/{ticketId}/appointments")
    public ResponseEntity<AppointmentCreatedResponse> createAppointment(
            @PathVariable UUID ticketId,
            @Valid @RequestBody CreateAppointmentRequest body,
            HttpServletRequest req
    ) {
        RequestContext ctx = RequestContexts.require(req);
        AppointmentCreatedResponse resp = appointmentService.createForTicket(
                ctx, ticketId, body);
        String etag = "\"" + resp.version() + "\"";
        return ResponseEntity.status(HttpStatus.CREATED).eTag(etag).body(resp);
    }

    @PostMapping
    public ResponseEntity<TicketCreatedResponse> create(
            @NotBlank
            @RequestHeader(value = "Idempotency-Key", required = true)
            String idemKey,
            @Valid @RequestBody CreateTicketRequest body,
            HttpServletRequest req
    ) {
        RequestContext ctx = RequestContexts.require(req);
        TicketCreatedResponse resp = ticketService.create(ctx, idemKey, body);
        String etag = "\"" + resp.version() + "\"";
        return ResponseEntity.status(HttpStatus.CREATED).eTag(etag).body(resp);
    }

    @GetMapping("/{id}")
    public ResponseEntity<TicketDetailResponse> get(@PathVariable UUID id, HttpServletRequest req) {
        RequestContext ctx = RequestContexts.require(req);
        TicketDetailResponse resp = ticketService.get(ctx, id);
        String etag = "\"" + resp.ticket().version() + "\"";
        return ResponseEntity.status(HttpStatus.OK).eTag(etag).body(resp);
    }

    @GetMapping
    public PageResponse<TicketResponse> list(
            @Valid @NotNull TicketQuery query,
            @PageableDefault(size = 20) Pageable pageable,
            HttpServletRequest req
    ) {
        RequestContext ctx = RequestContexts.require(req);
        return PageResponse.from(ticketService.list(ctx, query, pageable));
    }

    /*
    * 更强语义的先读后写, 并且要求版本号保证乐观锁(宏观)
    *
    * */
    @PostMapping("/{id}/transition")
    public ResponseEntity<Void> transition(
            @PathVariable UUID id,
            @NotNull @NotBlank
            @Pattern(regexp="^\"\\d+\"$", message="invalid If-Match")
            @RequestHeader(value = "If-Match", required = true)
            String ifMatch,
            @Valid @RequestBody TicketTransitionRequest body,
            HttpServletRequest req
            ) {
        RequestContext ctx = RequestContexts.require(req);
        ticketService.transition(ctx, id, ifMatch, body.toStatus());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/assign")
    public ResponseEntity<Void> assign(
            @PathVariable UUID id,
            @NotNull @NotBlank
            @Pattern(regexp="^\"\\d+\"$", message="invalid If-Match")
            @RequestHeader(value = "If-Match", required = true)
            String ifMatch,
            @Valid @RequestBody TicketAssignRequest body,
            HttpServletRequest req
    ) {
        RequestContext ctx = RequestContexts.require(req);
        ticketService.assign(ctx, id, ifMatch, body.newAssigneeId());
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}")
    public ResponseEntity<Void> update(
            @PathVariable UUID id,
            @NotNull @NotBlank
            @Pattern(regexp="^\"\\d+\"$", message="invalid If-Match")
            @RequestHeader(value = "If-Match", required = true)
            String ifMatch,
            @Valid @RequestBody UpdateTicketRequest body,
            HttpServletRequest req
    ) {
//        System.out.println("path ticket received...___--->");
        RequestContext ctx = RequestContexts.require(req);
        ticketService.update(ctx, id, ifMatch, body);
        return ResponseEntity.noContent().build();
    }
}
