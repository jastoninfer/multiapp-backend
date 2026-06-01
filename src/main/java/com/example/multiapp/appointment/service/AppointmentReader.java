package com.example.multiapp.appointment.service;

import com.example.multiapp.appointment.entity.Appointment;
import com.example.multiapp.appointment.repo.AppointmentRepository;
import com.example.multiapp.common.api.NotFoundException;
import com.example.multiapp.common.auth.EntityReader;
import com.example.multiapp.common.tenant.RequestContext;
import com.example.multiapp.ticket.repo.TicketRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

import static com.example.multiapp.membership.model.MembershipRole.CUSTOMER;

@Component
@RequiredArgsConstructor
public class AppointmentReader extends EntityReader {
//    private final AppointmentRepository appointmentRepo;
    private final TicketRepository ticketRepo;
    protected String entityName() {
        return "appointment";
    }
    public void requireVisible(RequestContext ctx, Appointment a) {
        // 考虑req的身份
        requireSameTenant(ctx, a);
        switch (ctx.role()) {
            case CUSTOMER -> { notFound(); return; }
            case RESOURCE_USER -> {
                // 仅当关联时才可以
                if(!a.getResourceUserId().equals(ctx.userId())) {
                    notFound();
                    return;
                }
            }
            case AGENT -> {
                if(!ticketRepo.existsByIdTenantIdAndIdIdAndOwnerUserId(
                        a.getId().getTenantId(), a.getTicketId(), ctx.userId())) {
                    notFound();
                    return;
                }
            }
            case ADMIN -> { return; }
        }
    }

    private void requireSameTenant(RequestContext ctx, Appointment a) {
        if(!a.getId().getTenantId().equals(ctx.tenantId())) {
            notFound();
        }
    }
}
