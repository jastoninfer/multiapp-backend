package com.example.multiapp.user.dto;

import com.example.multiapp.user.entity.AppUser;

import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

public record MeResponse(UUID userId, String email, String displayName,
                         String phone, String status, OffsetDateTime createdAt) {
    public MeResponse {
        Objects.requireNonNull(userId);
        Objects.requireNonNull(email);
        Objects.requireNonNull(displayName);
        Objects.requireNonNull(status);
        Objects.requireNonNull(createdAt);
    }
    public static MeResponse from(AppUser user) {
        Objects.requireNonNull(user, "user");
//        System.out.println("user is " + user);
        return new MeResponse(user.getId(), user.getEmail(), user.getDisplayName(),
                user.getPhone(), user.getUserStatus().name(), user.getCreatedAt());
    }
}
