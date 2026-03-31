package com.example.multiapp.resource.repo;

import com.example.multiapp.resource.entity.ResourceWorkingHours;
import com.example.multiapp.resource.entity.ResourceWorkingHoursId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ResourceWorkingHoursRepository extends JpaRepository<ResourceWorkingHours,
        ResourceWorkingHoursId> {
    List<ResourceWorkingHours> findByIdTenantIdAndIdResourceUserId(UUID tenantId, UUID resourceUserId);
}