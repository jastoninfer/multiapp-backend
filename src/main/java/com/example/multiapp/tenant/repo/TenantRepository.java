package com.example.multiapp.tenant.repo;

import com.example.multiapp.tenant.entity.Tenant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface TenantRepository extends JpaRepository<Tenant, UUID> {
    @Query("select count(t) > 0 from Tenant t where lower(trim(t.name)) = lower(trim(:name))")
    boolean existsByNameCi(@Param("name") String name);

    @Query("select t from Tenant t where lower(trim(t.name)) = lower(trim(:name))")
    Optional<Tenant> findByNameCi(@Param("name") String name);

}
