package com.example.multiapp.ticket.auth;

import com.example.multiapp.appointment.repo.AppointmentRepository;
import com.example.multiapp.common.api.NotFoundException;
import com.example.multiapp.common.api.PageResponse;
import com.example.multiapp.common.tenant.RequestContext;
import com.example.multiapp.common.user.UserReader;
import com.example.multiapp.contact.entity.Contact;
import com.example.multiapp.contact.repo.ContactRepository;
import com.example.multiapp.membership.model.MembershipRole;
import com.example.multiapp.membership.repo.TenantMembershipRepository;
import com.example.multiapp.ticket.dto.CreateTicketRequest;
import com.example.multiapp.ticket.dto.TicketQuery;
import com.example.multiapp.ticket.dto.UpdateTicketRequest;
import com.example.multiapp.ticket.entity.Ticket;
import com.example.multiapp.ticket.model.TicketStatus;
import com.example.multiapp.ticket.repo.TicketRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectReader;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import static com.example.multiapp.ticket.auth.TicketAction.READ;

@Service
@RequiredArgsConstructor
public class TicketAuthorizerImpl implements TicketAuthorizer{

    private final TicketRepository ticketRepo;
    private final ContactRepository contactRepo;
    private final AppointmentRepository appointmentRepo;
    private final TenantMembershipRepository membershipRepo;
    /*
    * 实际上这个权限系统可以说还是比较复杂的, 我们围绕4个角色(CUSTOMER, RESOURCE_USER, AGENT, ADMIN)
    * 以及多个query参数有一个比较复杂的权限矩阵, 有若干需要考虑的因素, 其中设计的表有ticket, app_user, tenant_membership
    * 以及contact, appointment
    * */
//    private final TenantMembershipRepository tenantMembershipRepo;

    // CREATE(C+S+A) :: CreateTicketRequest
    // LIST(C+S+A) :: ??QueryParameters
    // READ(C+S+A) :: UUID ticketId
    // UPDATE(C+S+A, 字段控制) :: UUID ticketId + UpdateTicketRequest(需要更新的字段)
    // CHANGE_ASSIGNEE(S: 自我指派+A) :: UUID ticketId + UUID newAssigneeId
    // CHANGE_STATUS(S+A, 人工流转只允许in_progress->closed) :: UUID ticketId + TicketStatus newStatus


    // 这个相对简单
    @Override
    public void requireCreate(RequestContext ctx, CreateTicketRequest req) {
        Objects.requireNonNull(ctx, "ctx");
        Objects.requireNonNull(req, "req");
        // 对req.requester_user_id | req.requester_contact_id的存在性检查放到service里做
        // admin+platform admin可以
        // agent可以给普通客户创建工单
        // resource_user/customer可以给自己创建工单
        if (ctx.role() == MembershipRole.RESOURCE_USER || ctx.role() == MembershipRole.CUSTOMER) {
            if (req.requesterUserId() == null || req.requesterUserId() != ctx.userId()) {
                throw new IllegalArgumentException("Customer or Resource User can only create ticket for themselves");
            }
        }
    }

    /*
    * 这个相对复杂, 因为query有很多optional字段, 每个和具体角色组合意义都不相同
    * 首先需要说明的是admin(包括platform_admin) 任何query字段都是可行的(在非null且数据确实存在于数据库中的情况下)
    *   - 数据虚构的问题可以交给数据库自己的一致性兜底机制解决
    * 主要考虑另外3中角色, CUSTOMER, RESOURCE_USER, AGENT
    * 1. 先考虑这三种角色都作为普通用户的情况, 他们理应可以忽略其他字段, 只关注requesterUserId or requesterContactId
    *   - 这两个字段要么两个全空, 要么有一个为空
    *   - 如果requesterUserId 非空且 = ctx.userId(), 可以
    *   - 如果requesterContactId 非空且 CONTACT[tenant_id, requesterContactId]存在且其linked_user_id = ctx.userId()
    *   - 可以
    * 2. 考虑他们作为各自非普通用户专用角色的权限
    *   - CUSTOMER: 这个没有额外的权限, 略过
    *   - RESOURCE_USER: 这个可以查看通过appointment表关联到的ticket, 具体来说appointment[tenant_id,resource_user_id]
    *   - .ticket_id => ticket, 可以额外查看这些ticket
    *   - AGENT: 可以额外查看那些owner_id.owner_user_id = ctx.userId()的那些tickets
    *
    * 3. 对于READ {ticketId} 来说 权限受理应该是类似的情况
    * 但实际上对于list来说, 这里应该放在service更合适, 因为query只是过滤, 如果没有不返回就好了
    * */
    @Transactional(readOnly = true)
    @Override
    public void requireList(RequestContext ctx, TicketQuery query) {
        Objects.requireNonNull(ctx, "ctx");
        // 各个角色都有访问的权限

//        if (ctx.role() == MembershipRole.CUSTOMER || ctx.role() == MembershipRole.RESOURCE_USER) {
//            // 只看到自己相关的, 约束requester_user_id | requester_contact_id (这两个最多一个非null)
//            if (query.requesterUserId() != null && query.requesterUserId() != ctx.userId() ||
//            query.requesterContactId() != null && contactRepo.findByIdTenantIdAndIdId(
//                    ctx.tenantId(), query.requesterContactId()).orElseThrow(() ->
//                    new IllegalArgumentException("no such contact [%s] under tenant [%s]".formatted(
//                            query.requesterContactId(), ctx.tenantId()))).getLinkedUserId()
//            != ctx.userId()) {
//                throw new AccessDeniedException("CUSTOMER or RESOURCE USER can only access their own tickets");
//            }
//        } else if (ctx.role() == MembershipRole.AGENT) {
//            // 可以查看assign给自己的tickets
//            if(query.assigneeId() != null && query.assigneeId() != ctx.userId()) {
//                throw new AccessDeniedException("AGENT can not access tickets not assigned to them");
//            }
//        }
    }

    /*
    * read由于直接限定了ticketId, 所以必须要给出一个能否访问的说法出来, 那么应该怎么做呢?
    * 很简单, 匹配list的做法就好了, 只不过这里可以要把判断逻辑前移到这里
    * */
    @Transactional(readOnly = true)
    @Override
    public void requireRead(RequestContext ctx, UUID ticketId) {
        Objects.requireNonNull(ctx, "ctx");
        Objects.requireNonNull(ticketId, "ticketId");
        UUID userId = Objects.requireNonNull(ctx.userId(), "ctx.userId");
        UUID tenantId = Objects.requireNonNull(ctx.tenantId(), "ctx.tenantId");
        Ticket ticket = ticketRepo.findByIdTenantIdAndIdId(ctx.tenantId(), ticketId).orElseThrow(
                () -> new NotFoundException("ticket: [%s] not found under tenant: [%s]"
                        .formatted(ticketId, ctx.tenantId())));
        if(ticket.getRequesterUserId() == userId) return;
        UUID contactId = ticket.getRequesterContactId();
        if(contactId != null && contactRepo.existsByIdTenantIdAndIdIdAndLinkedUserId(
                tenantId, contactId, userId)) return;
        switch (ctx.role()) {
            case CUSTOMER -> {}
            case RESOURCE_USER -> {
                if(appointmentRepo.existsByIdTenantIdAndTicketIdAndResourceUserId(
                        tenantId, ticketId, userId)) return;
            }
            case AGENT -> {
                if(ticket.getOwnerUserId().equals(userId)) return;
            }
            case ADMIN -> {
                return;
            }
            default -> throw new IllegalArgumentException("Unhandled role: " + ctx.role());
        }
        throw new AccessDeniedException("not authorized to access ticket: [%s]".formatted(ticketId));
    }

    /*
    * 精细化权限管理, 只有当目前ticket.owner_id为null时才允许agent将自己设为assignee(owner)
    * admin不受此约束
    * */
    @Transactional(readOnly = true)
    @Override
    public void requireReassign(RequestContext ctx, UUID ticketId, UUID newAssigneeId) {
        Objects.requireNonNull(ctx, "ctx");
        Objects.requireNonNull(ticketId, "ticketId");
        Objects.requireNonNull(newAssigneeId, "newAssigneeId");
        Ticket ticket = ticketRepo.findByIdTenantIdAndIdId(ctx.tenantId(), ticketId).orElseThrow(
                () -> new NotFoundException("ticket: [%s] not found".formatted(ticketId)));
        switch (ctx.role()) {
            case CUSTOMER, RESOURCE_USER -> {}
            case AGENT -> {
                if(ticket.getOwnerUserId() == null && newAssigneeId.equals(ctx.userId())) return;
            }
            case ADMIN -> {
                // 需要额外一点, admin具有次权限, 但是要求assignee至少是admin/agent
                if(membershipRepo.findByIdTenantIdAndIdUserId(ctx.tenantId(), newAssigneeId)
                        .orElseThrow(() -> new NotFoundException("Assignee: [%s] not in tenant: [%s]"
                                .formatted(newAssigneeId, ctx.tenantId())))
                        .getRole().canBeTicketOwner()) {
                    return;
                }
            }
            default -> throw new IllegalArgumentException("Unhandled role: " + ctx.role());
        }
        throw new AccessDeniedException("not authorized assign ticket");
    }

    /*
    * agent可以将ticket由in_progress转为closed
    * 系统自动状态流转不经过此方法(new->in_progress, closed->reopened)
    * */
    @Override
    public void requireTransition(RequestContext ctx, UUID ticketId, TicketStatus newStatus) {
        Objects.requireNonNull(ctx, "ctx");
        Objects.requireNonNull(ticketId, "ticketId");
        Objects.requireNonNull(newStatus, "newStatus");
        Ticket ticket = ticketRepo.findByIdTenantIdAndIdId(ctx.tenantId(), ticketId).orElseThrow(
                () -> new NotFoundException("ticket: [%s] not found".formatted(ticketId)));
        switch (ctx.role()) {
            case CUSTOMER, RESOURCE_USER -> {}
            case AGENT -> {
                if(ticket.getOwnerUserId().equals(ctx.userId()) && newStatus == TicketStatus.CLOSED)
                    return;
//                if(newStatus == TicketStatus.CLOSED) return;
            }
            case ADMIN -> {
                return;
            }
            default -> throw new IllegalArgumentException("Unhandled role: " + ctx.role());
        }
        throw new AccessDeniedException("not authorized transition ticket");
    }

    @Transactional(readOnly = true)
    @Override
    public void requireUpdate(RequestContext ctx, UUID ticketId, UpdateTicketRequest req) {
        Objects.requireNonNull(ctx, "ctx");
        Objects.requireNonNull(ticketId, "ticketId");
        Objects.requireNonNull(req, "req");
        Ticket ticket = ticketRepo.findByIdTenantIdAndIdId(ctx.tenantId(), ticketId).orElseThrow(
                () -> new NotFoundException("ticket: [%s] not found".formatted(ticketId)));
        // 基础逻辑, 不论身份都可以通过此通过检测
        if(req.priority() == null && req.ticketType() == null) {
            if(ticket.getRequesterUserId() != null) {
                if(ticket.getRequesterUserId().equals(ctx.userId())) return;
            } else if(contactRepo.existsByIdTenantIdAndIdIdAndLinkedUserId(
                    ctx.tenantId(), ticket.getRequesterContactId(), ctx.userId()))
                return;
        }
        // 专有逻辑
        switch (ctx.role()) {
            case CUSTOMER, RESOURCE_USER -> {}
            case AGENT -> {
                if(ticket.getOwnerUserId().equals(ctx.userId()) && req.ticketType() == null) return;
            }
            case ADMIN -> { return; }
            default -> throw new IllegalArgumentException("Unhandled role: " + ctx.role());
        }
        throw new AccessDeniedException("not authorized to update the ticket with given arguments");
    }
}
