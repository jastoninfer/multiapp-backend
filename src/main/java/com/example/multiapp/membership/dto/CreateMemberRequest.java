package com.example.multiapp.membership.dto;

import com.example.multiapp.membership.model.MembershipRole;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record CreateMemberRequest(
        @NotNull
        UUID userId,
        @NotNull
        MembershipRole role,
        boolean isDefault
) {
    public String toStableString() {
        return "userId=" + userId + "\nrole=" + role.name() + "\nisDefault" +
                (isDefault ? "true" : "false");
    }
}
