package com.example.multiapp.membership.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record MemberUserInfo(
        UUID userId,
        String email,
        String displayName,
        String role,
        String status,
        boolean isDefault,
        OffsetDateTime createdAt,
        long version
) {}
