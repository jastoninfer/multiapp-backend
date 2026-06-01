package com.example.multiapp.appointment.repo;

import com.example.multiapp.appointment.entity.Appointment;
import com.example.multiapp.appointment.model.AppointmentStatus;
import com.example.multiapp.membership.entity.TenantMembership;
import com.example.multiapp.membership.model.MembershipRole;
import com.example.multiapp.membership.repo.TenantMembershipRepository;
import com.example.multiapp.tenant.entity.Tenant;
import com.example.multiapp.tenant.repo.TenantRepository;
import com.example.multiapp.testinfra.PostgresContainerBase;
import com.example.multiapp.ticket.entity.Ticket;
import com.example.multiapp.ticket.model.TicketPriority;
import com.example.multiapp.ticket.model.TicketType;
import com.example.multiapp.ticket.repo.TicketRepository;
import com.example.multiapp.user.entity.AppUser;
import com.example.multiapp.user.repo.AppUserRepository;
import com.fasterxml.jackson.datatype.jsr310.deser.key.OffsetTimeKeyDeserializer;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;

@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@DataJpaTest
@ActiveProfiles("test")
public class AppointmentConstraintsIT extends PostgresContainerBase {
    @Autowired
    AppointmentRepository appointmentRepo;

    @Autowired
    TenantRepository tenantRepo;

    @Autowired
    TicketRepository ticketRepo;

    @Autowired
    AppUserRepository appUserRepo;

    @Autowired
    TenantMembershipRepository tenantMembershipRepo;

    UUID tenantId;
    UUID resourceUserId;
    UUID customerUserId;
    UUID agentUserId;
    UUID ticketId;


    @BeforeEach
    @Transactional
    void setUp() {
        Tenant tenant =  Tenant.create("tenant");
        tenantRepo.save(tenant);
        tenantRepo.flush();
        tenantId = tenant.getId();

        AppUser customerUser = AppUser.create("issuer-customer", "sub-customer",
                "email-customer", "displayName-customer", null, false);
        appUserRepo.save(customerUser);

        AppUser user = AppUser.create("issuer", "sub", "email",
                "displayName", null, false);
        appUserRepo.save(user);

        AppUser resourceUser = AppUser.create("issuer-resource", "sub-resource",
                "email-resource", "displayName-resource", null, false);
        appUserRepo.save(resourceUser);

        appUserRepo.flush();
        customerUserId = customerUser.getId();
        agentUserId = user.getId();
        resourceUserId = resourceUser.getId();

        TenantMembership membership =  TenantMembership.create(tenantId, agentUserId,
                MembershipRole.AGENT, false);
        tenantMembershipRepo.save(membership);
        TenantMembership customerMembership =  TenantMembership.create(tenantId, customerUserId,
                MembershipRole.CUSTOMER, false);
        tenantMembershipRepo.save(customerMembership);
        TenantMembership resourceMembership =  TenantMembership.create(tenantId, resourceUserId,
                MembershipRole.RESOURCE_USER, false);
        tenantMembershipRepo.save(resourceMembership);
        tenantMembershipRepo.flush();

        Ticket t = Ticket.create(tenantId, agentUserId, customerUserId, null,
                TicketPriority.MEDIUM, TicketType.SERVICE_REQUEST, "ticket",
                null, null);
        ticketRepo.save(t);
        ticketRepo.flush();
        ticketId = t.getId().getId();
    }

    @Test
    @Transactional
    void sameResourceOverlappingActiveAppointments_shouldFail() {

        Appointment a1 = Appointment.create(tenantId, ticketId, resourceUserId, customerUserId, null,
                OffsetDateTime.parse("2026-04-10T10:00:00+08:00"),
                OffsetDateTime.parse("2026-04-10T11:00:00+08:00"),
                null, null);
        appointmentRepo.save(a1);
        Appointment a2 = Appointment.create(tenantId, ticketId, resourceUserId, customerUserId, null,
                OffsetDateTime.parse("2026-04-10T10:30:00+08:00"),
                OffsetDateTime.parse("2026-04-10T11:30:00+08:00"),
                null, null);
        appointmentRepo.save(a2);
        assertThrows(DataIntegrityViolationException.class, appointmentRepo::flush);
    }

    @Test
    @Transactional
    void cancelledShouldNotBlockNewBooking() {
        Appointment cancelled = Appointment.create(tenantId, ticketId, resourceUserId, customerUserId, null,
                OffsetDateTime.parse("2026-04-10T10:00:00+08:00"),
                OffsetDateTime.parse("2026-04-10T11:00:00+08:00"),
                null, null);
        cancelled.cancel();
        appointmentRepo.save(cancelled);
        appointmentRepo.flush();
        Appointment booked = Appointment.create(tenantId, ticketId, resourceUserId, customerUserId, null,
                OffsetDateTime.parse("2026-04-10T10:30:00+08:00"),
                OffsetDateTime.parse("2026-04-10T11:30:00+08:00"),
                null, null);
        appointmentRepo.save(booked);
        // should not throw exception
        appointmentRepo.flush();
    }

    @Test
    @Transactional
    //  验证db层兜底的有效性, 不依赖服务层拦截
    void endBeforeStart_shouldFail() {
        OffsetDateTime start = OffsetDateTime.parse("2026-04-10T10:00:00+08:00");
        OffsetDateTime end = OffsetDateTime.parse("2026-04-10T11:00:00+08:00");
        Appointment a = Appointment.create(tenantId, ticketId, resourceUserId, customerUserId, null,
                start, end,null, null);
        a.reschedule(end, start);
        // 使用reschedule是为了绕过直接构造start>end
        // 被Appointment.create函数拦截
        appointmentRepo.save(a);
        assertThrows(DataIntegrityViolationException.class, appointmentRepo::flush);
    }
}
