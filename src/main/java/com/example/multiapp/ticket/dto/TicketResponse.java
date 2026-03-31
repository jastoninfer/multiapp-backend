package com.example.multiapp.ticket.dto;

import com.example.multiapp.ticket.entity.Ticket;
import com.example.multiapp.ticket.model.TicketPriority;
import com.example.multiapp.ticket.model.TicketStatus;
import com.example.multiapp.ticket.model.TicketType;

import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

/*
* 输出DOT: 通常不会走Bean Validation, 在服务端通过from(Ticket)映射
* 避免内部代码传入null值, 不依赖Spring/校验器/代理
* */

public record TicketResponse (
        UUID id,
        long ticketNo,
        long version,
        TicketStatus status,
        TicketPriority priority,
        TicketType type,
        UUID ownerUserId,
        String ownerName,
        UUID requesterUserId,
        UUID requesterContactId,
        String requesterName,
        String title,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        long commentCount,
        long attachmentCount,
        OffsetDateTime nextAppointmentAt ) {}
