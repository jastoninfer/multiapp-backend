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

    @Query(value = """
    select * from app.resource_block b
        where b.tenant_id = :tenantId
            and b.resource_user_id = :resourceUserId
            and b.deleted_at is null
            and (cast(:from as timestamptz) is null or b.end_at > :from)
            and (cast(:to as timestamptz) is null or b.start_at < :to)
        order by b.start_at asc
    """, nativeQuery = true)
    List<ResourceBlock> listInRange(
            @Param("tenantId") UUID tenantId,
            @Param("resourceUserId") UUID resourceUserId,
            @Param("from") OffsetDateTime from,
            @Param("to") OffsetDateTime to);
}
