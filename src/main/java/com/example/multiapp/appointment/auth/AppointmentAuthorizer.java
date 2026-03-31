package com.example.multiapp.appointment.auth;

import com.example.multiapp.appointment.dto.AppointmentQuery;
import com.example.multiapp.appointment.dto.CreateAppointmentRequest;
import com.example.multiapp.appointment.dto.UpdateAppointmentRequest;
import com.example.multiapp.common.tenant.RequestContext;

import java.util.UUID;

public interface AppointmentAuthorizer {
    // 创建预约: 仅限admin+agent
    // 预约日程查询(列表): admin+agent+resource_user(只能查自己相关的), 需要payload
    // 查看预约单条详情: 同列表查询
    // 修改预约: admin+agent, resource_user只能修改自己负责的预约并且限制字段
    void requireCreate(RequestContext ctx, UUID ticketId, CreateAppointmentRequest req);
    void requireSearch(RequestContext ctx, AppointmentQuery query);
    void requireRead(RequestContext ctx, UUID appointmentId);
    void requireUpdate(RequestContext ctx, UUID appointmentId, UpdateAppointmentRequest req);
}
