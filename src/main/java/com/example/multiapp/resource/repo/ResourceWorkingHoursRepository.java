package com.example.multiapp.resource.repo;

import com.example.multiapp.resource.entity.ResourceWorkingHours;
import com.example.multiapp.resource.entity.ResourceWorkingHoursId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface ResourceWorkingHoursRepository extends JpaRepository<ResourceWorkingHours,
        ResourceWorkingHoursId> {
    @Modifying
    @Query("""
        delete from ResourceWorkingHours r
            where r.id.tenantId = :tenantId
                and r.id.resourceUserId = :resourceUserId
    """)
    int deleteByTenantIdAndIdResourceUserId(
            @Param("tenantId") UUID tenantId,
            @Param("resourceUserId") UUID resourceUserId);

    List<ResourceWorkingHours> findByIdTenantIdAndIdResourceUserId(UUID tenantId, UUID resourceUserId);
}