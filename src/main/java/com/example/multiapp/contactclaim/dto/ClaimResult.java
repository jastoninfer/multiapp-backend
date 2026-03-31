package com.example.multiapp.contactclaim.dto;

import java.util.UUID;

public record ClaimResult(
        UUID tenantId,
        UUID contactId
) {}
