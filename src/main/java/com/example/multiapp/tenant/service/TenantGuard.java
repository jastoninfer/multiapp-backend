package com.example.multiapp.tenant.service;

import com.example.multiapp.common.api.ForbiddenException;
import com.example.multiapp.common.api.NotFoundException;
import com.example.multiapp.tenant.entity.Tenant;
import com.example.multiapp.tenant.model.TenantStatus;
import com.example.multiapp.tenant.repo.TenantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TenantGuard {
    private final TenantRepository tenantRepo;

    @Transactional(readOnly = true)
    public void requireActiveTenant(UUID tenantId) {
        Objects.requireNonNull(tenantId, "tenantId");
        Tenant t =  tenantRepo.findById(tenantId).orElseThrow(() -> new NotFoundException("tenant not found"));
        if(t.getStatus() != TenantStatus.ACTIVE) {
            throw new ForbiddenException("Tenant status: %s".formatted(t.getId()));
        }
    }
}
