package com.example.multiapp.appointment.service;

import com.example.multiapp.appointment.auth.AppointmentAuthorizer;
import com.example.multiapp.appointment.dto.UpdateAppointmentRequest;
import com.example.multiapp.appointment.entity.Appointment;
import com.example.multiapp.appointment.entity.AppointmentId;
import com.example.multiapp.appointment.repo.AppointmentRepository;
import com.example.multiapp.common.api.ConflictException;
import com.example.multiapp.common.api.PreconditionFailedException;
import com.example.multiapp.common.tenant.RequestContext;
import com.example.multiapp.membership.model.MembershipRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class AppointmentUpdateServiceUnitTest {
    @Mock AppointmentRepository appointmentRepo;
    @Mock AppointmentAuthorizer appointmentAuth;
    AppointmentService appointmentService;

    @BeforeEach
    void setUp() {
        appointmentService = new AppointmentService(
                appointmentRepo, appointmentAuth, null, null,
                null, null, null
        );
    }

    @Test
    void update_ifMatchMisMatch_shouldThrowPreCondFailed_andNotSave() {
        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID appointmentId = UUID.randomUUID();
        RequestContext ctx = new RequestContext(
                tenantId, userId, false,
                MembershipRole.AGENT, "iss", "sub",
                "req"
        );
        Appointment a = mock(Appointment.class);
//        when(a.getId()).thenReturn(new AppointmentId(tenantId, appointmentId));
        when(a.getVersion()).thenReturn(3L);
        when(appointmentRepo.findByIdTenantIdAndIdId(tenantId, appointmentId)).thenReturn(Optional.of(a));
        assertThrows(PreconditionFailedException.class, () ->
                appointmentService.update(ctx, appointmentId, "\"2\"",
                        new UpdateAppointmentRequest(null,null,null,null,null,null)));
        verify(appointmentRepo, never()).save(any());
    }

    @Test
    void update_resourceUserReschedule_shouldNotSave() {
        UUID tenantId = UUID.randomUUID();
        UUID resourceUserId = UUID.randomUUID();
        UUID appointmentId = UUID.randomUUID();
        RequestContext ctx = new RequestContext(tenantId, resourceUserId, false,
                MembershipRole.RESOURCE_USER, "iss", "sub", "req");
        Appointment a = mock(Appointment.class);
//        when(a.getId()).thenReturn(new AppointmentId(tenantId, appointmentId));
        when(a.getVersion()).thenReturn(3L);
//        when(a.getResourceUserId()).thenReturn(resourceUserId);
        when(appointmentRepo.findByIdTenantIdAndIdId(tenantId, appointmentId)).thenReturn(Optional.of(a));
        UpdateAppointmentRequest request = new UpdateAppointmentRequest(null,
                OffsetDateTime.parse("2026-04-10T14:00:00+08:00"),
                OffsetDateTime.parse("2026-04-10T15:00:00+08:00"),
                null,null,null);
//        assertThrows(BadRequestException.class, () -> appointmentService.update(
//                ctx, appointmentId, "3", request
//        ));
        appointmentService.update(ctx, appointmentId, "\"3\"", request);
        verify(appointmentRepo, never()).save(any());
    }
}
