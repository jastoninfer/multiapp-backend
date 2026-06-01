package com.example.multiapp.appointment.service;

import com.example.multiapp.appointment.auth.AppointmentAuthorizer;
import com.example.multiapp.appointment.dto.AppointmentCreatedResponse;
import com.example.multiapp.appointment.dto.CreateAppointmentRequest;
import com.example.multiapp.appointment.entity.Appointment;
import com.example.multiapp.appointment.repo.AppointmentRepository;
import com.example.multiapp.common.aduit.AuditWriter;
import com.example.multiapp.common.api.ConflictException;
import com.example.multiapp.common.outbox.OutboxPublisher;
import com.example.multiapp.common.tenant.RequestContext;
import com.example.multiapp.membership.model.MembershipRole;
import com.example.multiapp.ticket.entity.Ticket;
import com.example.multiapp.user.service.UserGuard;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class AppointmentServiceUnitTest {

    @Mock
    AppointmentAuthorizer appointmentAuth;
    @Mock
    AppointmentRepository appointmentRepo;
    @Mock
    AvailabilityValidator availabilityValidator;
    @Mock
    AuditWriter auditWriter;
    @Mock
    UserGuard userGuard;
    @Mock
    OutboxPublisher outboxPublisher;
    @Mock AppointmentReader appointmentReader;

    AppointmentService appointmentService;

    /*
    * @Mock做的事，是在每个测试方法运行前，mockito自动给这个字段赋一个替身对象（mock）
    * 这个替身对象：
    * - 实现、继承了同样的类型（比如AppointmentRepository）
    * - 默认行为是空的: 调用方法不会做真实逻辑，返回默认值（对象返回null，boolean返回
    * false，集合返回空/0等），也不会访问数据库
    * - 会记录交互：你之后verify(appointmentRepo).save(...)就是问mock: 你有没有被
    * 这样调用过、调用几次、参数是什么
    *
    * 要测试的对象就是AppointmentService本体, 被测对象(SUT, system under test)
    * 是真实对象, new AppointmentService(...)
    * 依赖(collaborators): 用mock替代(repo/auth/audit/outbox)
    * */
    @BeforeEach
    void setUp() {
        appointmentService = new AppointmentService(
                appointmentRepo,
                appointmentAuth,
                auditWriter,
                outboxPublisher,
                userGuard,
                availabilityValidator,
                appointmentReader
        );
    }

    // Appointment::createForTicket
    @Test
    void createForTicket_success_shouldValidateAvailability_save_audit_outbox() {
        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID ticketId = UUID.randomUUID();
        String requestId = "req-123";
        RequestContext ctx = new RequestContext(
          tenantId, userId, false,
          /*role*/ MembershipRole.AGENT, "issuer", "sub", requestId
        );
        UUID resourceUserId = UUID.randomUUID();
        OffsetDateTime startAt = OffsetDateTime.parse("2026-04-10T10:00:00+08:00");
        OffsetDateTime endAt = OffsetDateTime.parse("2026-04-10T11:00:00+08:00");
        UUID customerUserId = UUID.randomUUID();
        CreateAppointmentRequest createAppointmentRequest = new CreateAppointmentRequest(
          resourceUserId, customerUserId, null, startAt, endAt, "addr", "notes"
        );
        // 1) auth放行
        doNothing().when(appointmentAuth).requireCreate(ctx, ticketId, createAppointmentRequest);
        // 2) availability 放行
        doNothing().when(availabilityValidator).validate(tenantId, resourceUserId, startAt, endAt);
        // 3) repo.save: 你service里会Appointment.from(...)生成实体并save
        // 这里我们不关系实体怎么构建, 但要保证save之后a.getId().getId()能用
        // 由于from(...)是静态方法, 单元测试里不方便mock
        // 所以我们只验证save被调用, flush被调用, 副作用被触发即可
//        doNothing().when(appointmentRepo).save(any(Appointment.class));
        // 执行
        AppointmentCreatedResponse resp = appointmentService.createForTicket(
                ctx, ticketId, createAppointmentRequest
        );
        assertNotNull(resp);
        // verify 调用顺序 (关键链路)
        verify(appointmentAuth).requireCreate(ctx, ticketId, createAppointmentRequest);
        verify(availabilityValidator).validate(tenantId, resourceUserId, startAt, endAt);

        // save至少被调用一次
        verify(appointmentRepo).save(any(Appointment.class));
//        verify(appointmentRepo).flush();

        // audit/outbox被调用 (不强行断言payload细节, 除非你想非常严格)
        verify(auditWriter).append(any());
        verify(outboxPublisher).publish(any());
        verifyNoMoreInteractions(appointmentRepo);
    }

    // Appointment::createForTicket
    @Test
    void createForTicket_whenDbOverlap_shouldThrowDataIntegrity_andNotWriteAuditOutbox() {
        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID ticketId = UUID.randomUUID();
        UUID customerUserId = UUID.randomUUID();
        String requestId = "req-273";
        RequestContext ctx = new RequestContext(
                tenantId, userId, false,
                /*role*/ MembershipRole.AGENT, "issuer", "sub", requestId
        );
        UUID resourceUserId = UUID.randomUUID();
        OffsetDateTime startAt = OffsetDateTime.parse("2026-04-10T10:00:00+08:00");
        OffsetDateTime endAt = OffsetDateTime.parse("2026-04-10T11:00:00+08:00");
        CreateAppointmentRequest createAppointmentRequest = new CreateAppointmentRequest(
          resourceUserId, customerUserId, null, startAt, endAt, "addr", "notes"
        );
        doNothing().when(appointmentAuth).requireCreate(ctx, ticketId, createAppointmentRequest);
        doNothing().when(availabilityValidator).validate(tenantId, resourceUserId, startAt, endAt);

        // 模拟db exclusion/unique 冲突: 通常在flush时抛出
        doThrow(new DataIntegrityViolationException("overlap"))
                .when(appointmentRepo).save(any(Appointment.class));
        DataIntegrityViolationException ex = assertThrows(
                DataIntegrityViolationException.class,
                () -> appointmentService.createForTicket(ctx, ticketId, createAppointmentRequest)
        );
        assertTrue(ex.getMessage().toLowerCase().contains("overlap"));
        // 发生DB冲突后, 不应写audit/outbox
        verify(auditWriter, never()).append(any());
        verify(outboxPublisher, never()).publish(any());
    }
}
