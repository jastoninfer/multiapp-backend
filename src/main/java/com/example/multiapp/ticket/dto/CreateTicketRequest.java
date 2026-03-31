package com.example.multiapp.ticket.dto;

import com.example.multiapp.ticket.model.TicketPriority;
import com.example.multiapp.ticket.model.TicketType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.Objects;
import java.util.UUID;

public record CreateTicketRequest (
        UUID requesterUserId,
        UUID requesterContactId,
        // 禁止ascii控制字符 + 不允许换行
        @Pattern(regexp = "^[^\\p{Cc}\\r\\n]+$", message = "invalid title")
        @NotNull @NotBlank @Size(max = 200) String title,
        // comment 应该基本同description, 通常更短点
        @Pattern(regexp = "^(?:[^\\p{Cc}]|[\\r\\n\\t])*$", message = "invalid description")
        @Size(max = 4000) String description,
        @NotNull TicketPriority priority,
        @NotNull TicketType ticketType,
        // 类似title的方法处理locationText
        @Pattern(regexp = "^[^\\p{Cc}\\r\\n]+$", message = "invalid location text")
        @Size(max=100) String locationText
){
    public CreateTicketRequest {
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(priority, "priority");
        Objects.requireNonNull(ticketType, "ticketType");

        boolean userPresent = requesterUserId != null;
        boolean contactPresent = requesterContactId != null;
        if (userPresent == contactPresent) {
            throw new IllegalArgumentException("exactly one of requesterUserId or requesterContactId must be set");
        }

        description = normalizeOptional(description);
        locationText = normalizeOptional(locationText);
        title = title.strip();
    }

    public String toStableString() {
        String requester = (requesterUserId != null) ? "user:" + requesterUserId
                : "contact:" + requesterContactId;
        String d = (description == null) ? "" : description;
        String loc = (locationText == null) ? "" : locationText;
        return "requester=" + requester
                + "|title=" + escAndNormalize(title)
                + "|priority=" + priority.name()
                + "|ticketType=" + ticketType.name()
                + "|locationText" + escAndNormalize(loc);
    }

    private static String escAndNormalize(String s) {
        // 只转义我们用到的分隔符和转义符本身, 保证可逆且稳定
        return s.replace("\\", "\\\\")
                .replace("|", "\\|")
                .replace("=", "\\=")
                .replaceAll("\\s+", " ");
    }

    private static String normalizeOptional(String s) {
        return (s == null || s.isBlank()) ? null : s.strip();
    }
}
