package com.example.multiapp.contactclaim.dto;

import jakarta.annotation.Nullable;

public record ClaimCodeRequest(
        int expiresInMinutes) {}
