package com.example.multiapp.membership.dto;

import com.example.multiapp.membership.model.MembershipRole;
import jakarta.validation.constraints.NotNull;

public record UpdateMemberRequest(
        @NotNull MembershipRole targetRole) {}
