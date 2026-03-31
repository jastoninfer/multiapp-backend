package com.example.multiapp.ticket.dto;

/*
* 一个ticket+若干子资源摘要列表, 详情响应里放有限长度的list(例如最近N条评论, 未来/最近N条预约, 前N个附件)
* 并返回各自的totalCount+nextCursor/hasMore
* 为每个子资源提供独立分页端点
*   - GET /tickets/{id}/comments?page=&size=
*   - GET /tickets/{id}/attachments?page=&size=
*   - GET /tickets/{id}/appointments?page=&size=
* 长度怎么定义
*   - comments: 默认返回最新20条(order by createdAt desc limit 20), 给totalCount, 需要更多走子端点
*   - attachments: 默认50或100条(通常数量不大, 但也可能很多), 同样给totalCount
*   - appointments: 默认返回未来的+最近的各10条, 或按时间排序取20条(上门预约通常前端更关心未来安排)
* 字段选择:
*   - TicketDetailResponse本体: TicketResponse本体+更完整字段(desc, location, requester_user_id,
*       requester_contact_id, priority, ticket_type, first_response_at, closed_at, updated_at)
*   - appointments: 不要塞一堆ticket字段, 只给预约自身需要的: id, startAt, endAt, status, resourceUserId
*       (含displayName), location, notes(可选), createdAt
*   - comments: id, authorUserId(含displayName), body(或markdown), createdAt, editedAt, visibility
*   - attachments: id, filename, contentType, sizeBytes, url(或downloadUrl), uploadedBy, createdAt,
*       checksum(可选). 不要把二进制内容塞进JSON.
* */

import com.example.multiapp.appointment.dto.AppointmentSummary;
import com.example.multiapp.attachment.dto.AttachmentSummary;
import com.example.multiapp.comment.dto.CommentSummary;
import com.example.multiapp.common.api.dto.SliceBlock;

public record TicketDetailResponse(
        TicketResponse ticket,
        SliceBlock<AppointmentSummary> upcomingAppointments,
        SliceBlock<AppointmentSummary> recentPastAppointments,
        SliceBlock<CommentSummary> comments,
        SliceBlock<AttachmentSummary> attachments
) {
}
