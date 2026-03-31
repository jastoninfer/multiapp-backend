package com.example.multiapp.ticket.dto;

import com.example.multiapp.ticket.model.TicketStatus;
import jakarta.validation.constraints.NotNull;

/*
 * 输入DTO: HTTP JSON -> Jackson反序列化 -> controller参数
 * 在框架入口同一校验
 * 确保@NotNull生效, controller参数需要用@Valid/@Validated, 例如
 * @RequstBody @Valid TransitionRequest req
 */
public record TicketTransitionRequest(
        @NotNull TicketStatus fromStatus,
        @NotNull TicketStatus toStatus
) {}
