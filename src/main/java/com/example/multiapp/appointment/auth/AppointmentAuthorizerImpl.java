package com.example.multiapp.appointment.auth;

import com.example.multiapp.appointment.dto.AppointmentQuery;
import com.example.multiapp.appointment.dto.CreateAppointmentRequest;
import com.example.multiapp.appointment.dto.UpdateAppointmentRequest;
import com.example.multiapp.appointment.entity.Appointment;
import com.example.multiapp.appointment.model.AppointmentStatus;
import com.example.multiapp.appointment.repo.AppointmentRepository;
import com.example.multiapp.appointment.service.AppointmentReader;
import com.example.multiapp.common.api.NotFoundException;
import com.example.multiapp.common.tenant.RequestContext;
import com.example.multiapp.contact.repo.ContactRepository;
import com.example.multiapp.membership.model.MembershipRole;
import com.example.multiapp.membership.repo.TenantMembershipRepository;
import com.example.multiapp.ticket.auth.TicketAuthorizer;
import com.example.multiapp.ticket.entity.Ticket;
import com.example.multiapp.ticket.repo.TicketRepository;
import lombok.RequiredArgsConstructor;
import com.example.multiapp.common.api.ForbiddenException;
import org.apache.coyote.BadRequestException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AppointmentAuthorizerImpl implements AppointmentAuthorizer{
    // 查看预约单条详情: 同列表查询
    private final AppointmentRepository appointmentRepo;
    private final TicketAuthorizer ticketAuth;
    private final TenantMembershipRepository membershipRepo;
    private final ContactRepository contactRepo;
    private final TicketRepository ticketRepo;
    private final AppointmentReader appointmentReader;

    @Transactional(readOnly = true)
    @Override
    public void requireRead(RequestContext ctx) {
        Objects.requireNonNull(ctx, "ctx");
        // 如果用户越权了, 什么都不返回就好了, 把异常行为 -> 0结果行为
        // 减少查表次数
        if(ctx.role() == MembershipRole.ADMIN || ctx.role() == MembershipRole.AGENT
                || ctx.role() == MembershipRole.RESOURCE_USER) return;
        throw new ForbiddenException("only admin or agent or resource user can view appointment details");
        // 如果是agent并且agent不负责该ticket, 报错
    }

    @Transactional(readOnly = true)
    // 创建预约: 仅限admin+agent
    @Override
    public void requireCreate(RequestContext ctx, UUID ticketId, CreateAppointmentRequest req) {
        Objects.requireNonNull(ctx, "ctx");
        if(ctx.role() != MembershipRole.ADMIN && ctx.role() != MembershipRole.AGENT) {
            throw new ForbiddenException("only admin or agent can create appointment");
        }
        UUID resourceUserId = Objects.requireNonNull(req.resourceUserId(), "resourceUserId");
        // 那只是基础条件, 我们还需要考虑的是: 1) 当前用户对于所谓的ticketId确实有可见权(同租户下)
        // 2) req.resourceUserId确实是当前租户下角色为RESOURCE_USER的用户(当然数据库也会用外键兜底,
        // 这里软件层面也会进行校验)
        ticketAuth.requireRead(ctx, ticketId, true);
        // 如果是agent并且agent不负责该ticket, 报错
        if(ctx.role() == MembershipRole.AGENT && !ticketRepo
                .existsByIdTenantIdAndIdIdAndOwnerUserId(
                        ctx.tenantId(), ticketId, ctx.userId())) {
            throw new ForbiddenException("agents can only access appointments related to" +
                    "tickets managed by them");
        }
//        if(ctx.role() == MembershipRole.AGENT &&) {}
        if(!membershipRepo.existsByIdTenantIdAndIdUserIdAndRole(ctx.tenantId(),
                resourceUserId, MembershipRole.RESOURCE_USER))
            throw new NotFoundException("resource user: [%s] not found in this tenant"
                    .formatted(resourceUserId));
        // 继续校验req.customerContactId + req.customerUserId字段
        if(req.customerUserId() != null && !membershipRepo.existsByIdTenantIdAndIdUserId(
                ctx.tenantId(), req.customerUserId()) || req.customerContactId() != null
            && !contactRepo.existsByIdTenantIdAndIdId(ctx.tenantId(), req.customerContactId())) {
            throw new NotFoundException("user or contact not found in this tenant");
        }
    }

    // 预约日程查询(列表): admin+agent+resource_user(只能查自己相关的), 需要payload
    // 有两种处理办法: 对于resource_user, 只能查自己相关的, 第一把query从record改为普通class
    // 从而在auth阶段对resourceUserId强制赋值等, 但这破坏了对象不可变性;
    // 第二种办法: 在service里判断当前角色, 如果使用resourceUSER, 使用专用repo方法搜索并仅将query.resourceUserId
    // 作为增强过滤条件, 本身repo专用方法会强制匹配resourceUserId :: ctx.userId [采取该方法]
    @Transactional(readOnly = true)
    @Override
    public void requireSearch(RequestContext ctx, AppointmentQuery query) {
        Objects.requireNonNull(ctx, "ctx");
        Objects.requireNonNull(query, "query");
        if(ctx.role() == MembershipRole.ADMIN || ctx.role() == MembershipRole.AGENT
            || ctx.role() == MembershipRole.RESOURCE_USER) return;
        throw new ForbiddenException("only admin or agent or resource user can list appointments");
    }

    // 修改预约: admin+agent, resource_user只能修改自己负责的预约并且限制字段
    // 同样地字段约束交给service层来做
    @Transactional(readOnly = true)
    @Override
    public void requireUpdate(RequestContext ctx, Appointment a, UpdateAppointmentRequest req) {
        Objects.requireNonNull(ctx, "ctx");
        Objects.requireNonNull(a, "appointment");
        Objects.requireNonNull(req, "UpdateAppointmentRequest");
        if (req.isEmpty()) throw new IllegalArgumentException("invalid request");
        appointmentReader.requireVisible(ctx, a); // 这里面已经包含了role的判断, 具有读权限
        // 对各个字段+role进行严格检查
        // 具体的更新原则
        // CUSTOMER: 无权限
        // RESOURCE_USER: 可以尝试将appointment标记为完成, 可以更新notes字段
        // AGENT: 可以更新address_text字段+notes字段
        // ADMIN: 完全的权限
        switch (ctx.role()) {
            case CUSTOMER -> {
                throw new ForbiddenException("only admin or agent or resource user can update appointments");
            }
            case RESOURCE_USER -> {
                // 只允许两种动作: 1. 将appointment标记为完成, 2. 更新notes字段 3. 确认已到达
                if(req.hasArrivedAtOnly() || req.hasNotesOnly() || req.hasMarkCompleteOnly()) {
                    return;
                }
            }
            case AGENT, ADMIN -> {
                // 允许的动作包括: 全部
                return;
            }
        }
        throw new ForbiddenException(("unauthorized to update appointment:" +
                " [ {%s} ] details/status").formatted(a));

//        if(ctx.role() == MembershipRole.ADMIN || ctx.role() == MembershipRole.AGENT
//                || ctx.role() == MembershipRole.RESOURCE_USER) return;
//        if(ctx.role() == MembershipRole.CUSTOMER)
//            throw new ForbiddenException("only admin or agent or resource user can update appointments");
//        if(ctx.role() == MembershipRole.RESOURCE_USER) {
//            // 只能更新自己负责的, 并且appt状态不能为完成或取消
//            if(a.getResourceUserId().equals(ctx.userId()) && (a.getStatus() == AppointmentStatus.BOOKED
//            || a.getStatus() == AppointmentStatus.RESCHEDULED)) return;
//        }else if(ctx.role() == MembershipRole.AGENT) {
//            // 只能更新自己负责的
//            if(ticketRepo.existsByIdTenantIdAndIdIdAndOwnerUserId(ctx.tenantId(),
//                    a.getTicketId(), ctx.userId())) {
//                return;
//            }
//        }else if(ctx.role() == MembershipRole.ADMIN) {
//            return;
//        }
//
//        throw new ForbiddenException("unauthorized to update appointment: [ {%s} ] details/status".formatted(a));
    }
}
