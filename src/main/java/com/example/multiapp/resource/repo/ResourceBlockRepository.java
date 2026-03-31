package com.example.multiapp.resource.repo;

import com.example.multiapp.resource.entity.ResourceBlock;
import com.example.multiapp.resource.entity.ResourceBlockId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.security.core.parameters.P;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ResourceBlockRepository extends JpaRepository<ResourceBlock, ResourceBlockId> {
    Optional<ResourceBlock> findByIdTenantIdAndIdId(UUID tenantId, UUID id);
    Optional<ResourceBlock> findByIdTenantIdAndIdIdAndResourceUserId(UUID tenantId, UUID id, UUID resourceUserId);

    @Query("""
    select b from ResourceBlock b
        where b.id.tenantId = :tenantId
            and b.resourceUserId = :resourceUserId
            and b.deletedAt is null
            and (:from is null or b.endAt > :from)
            and (:to is null or b.startAt < :to)
        order by b.startAt asc
    """)
    List<ResourceBlock> listInRange(
            @Param("tenantId") UUID tenantId,
            @Param("resourceUserId") UUID resourceUserId,
            @Param("from") OffsetDateTime from,
            @Param("to") OffsetDateTime to);
}
