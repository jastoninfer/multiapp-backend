package com.example.multiapp.appointment.auth;

import com.example.multiapp.appointment.dto.AppointmentQuery;
import com.example.multiapp.appointment.dto.CreateAppointmentRequest;
import com.example.multiapp.appointment.dto.UpdateAppointmentRequest;
import com.example.multiapp.appointment.repo.AppointmentRepository;
import com.example.multiapp.common.api.NotFoundException;
import com.example.multiapp.common.tenant.RequestContext;
import com.example.multiapp.contact.repo.ContactRepository;
import com.example.multiapp.membership.model.MembershipRole;
import com.example.multiapp.membership.repo.TenantMembershipRepository;
import com.example.multiapp.ticket.auth.TicketAuthorizer;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    @Transactional(readOnly = true)
    @Override
    public void requireRead(RequestContext ctx, UUID appointmentId) {
        Objects.requireNonNull(ctx, "ctx");
        Objects.requireNonNull(appointmentId, "appointmentId");
        // 如果用户越权了, 什么都不返回就好了, 把异常行为 -> 0结果行为
        // 减少查表次数
        if(ctx.role() == MembershipRole.ADMIN || ctx.role() == MembershipRole.AGENT
                || ctx.role() == MembershipRole.RESOURCE_USER) return;
        throw new AccessDeniedException("only admin or agent or resource user can view appointment details");
    }

    @Transactional(readOnly = true)
    // 创建预约: 仅限admin+agent
    @Override
    public void requireCreate(RequestContext ctx, UUID ticketId, CreateAppointmentRequest req) {
        Objects.requireNonNull(ctx, "ctx");
        if(ctx.role() != MembershipRole.ADMIN && ctx.role() != MembershipRole.AGENT) {
            throw new AccessDeniedException("only admin or agent can create appointment");
        }
        UUID resourceUserId = Objects.requireNonNull(req.resourceUserId(), "resourceUserId");
        // 那只是基础条件, 我们还需要考虑的是: 1) 当前用户对于所谓的ticketId确实有可见权(同租户下)
        // 2) req.resourceUserId确实是当前租户下角色为RESOURCE_USER的用户(当然数据库也会用外键兜底,
        // 这里软件层面也会进行校验)
        ticketAuth.requireRead(ctx, ticketId);
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
        throw new AccessDeniedException("only admin or agent or resource user can list appointments");
    }

    // 修改预约: admin+agent, resource_user只能修改自己负责的预约并且限制字段
    // 同样地字段约束交给service层来做
    @Transactional(readOnly = true)
    @Override
    public void requireUpdate(RequestContext ctx, UUID appointmentId, UpdateAppointmentRequest req) {
        Objects.requireNonNull(ctx, "ctx");
        Objects.requireNonNull(appointmentId, "appointmentId");
        Objects.requireNonNull(req, "UpdateAppointmentRequest");
        if(ctx.role() == MembershipRole.ADMIN || ctx.role() == MembershipRole.AGENT
                || ctx.role() == MembershipRole.RESOURCE_USER) return;
        throw new AccessDeniedException("only admin or agent or resource user can update appointments");
    }
}
