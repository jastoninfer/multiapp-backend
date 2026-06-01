package com.example.multiapp.membership.repo;

import com.example.multiapp.membership.dto.MemberUserInfo;
import com.example.multiapp.membership.entity.TenantMembership;
import com.example.multiapp.membership.entity.TenantMembershipId;
import com.example.multiapp.membership.model.MembershipRole;
import com.example.multiapp.user.dto.MeTenantResponse;
import org.apache.catalina.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.security.core.parameters.P;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TenantMembershipRepository extends
        JpaRepository<TenantMembership, TenantMembershipId> {
    Optional<TenantMembership> findByIdTenantIdAndIdUserId(UUID tenantId, UUID userId);
    boolean existsByIdTenantIdAndIdUserId(UUID tenantId, UUID userId);
    boolean existsByIdTenantIdAndIdUserIdAndRole(UUID tenantId, UUID userId, MembershipRole role);

    @Modifying
    @Query(value = """
    insert into app.tenant_membership(tenant_id, user_id, role, is_default)
    values (:tenantId, :userId, :role, false)
    on conflict(tenant_id, user_id) do nothing
    """, nativeQuery = true)
    int insertIgnore(
            @Param("tenantId") UUID tenantId,
            @Param("userId") UUID userId,
            @Param("role") String role
    );

    @Query(value = """
    select count(*) from(
        select m.tenant_id
            from app.tenant_membership m join app.app_user u on u.id = m.user_id
                where m.role = :role and u.status <> :disabledStatus
                    group by m.tenant_id
                    having count(*) = 1
                        and sum (case when m.user_id = :userId then 1 else 0 end) = 1
        ) x
    """, nativeQuery = true)
    long countTenantsWhereUserIsSoleActiveRole(
            @Param("userId") UUID userId,
            @Param("role") String role,
            @Param("disabledStatus") String disabledStatus);

    @Query("""
    select count(m) from TenantMembership  m join AppUser u on u.id = m.id.userId
        where m.id.tenantId = :tenantId
            and cast(m.role as string) = :role and cast(u.userStatus as string) <> :disabledStatus
    """)
    long countActiveMembersByRole(
            @Param("tenantId") UUID tenantId,
            @Param("role") String role,
            @Param("disabledStatus") String disabledStatus);

    @Query("""
    select new com.example.multiapp.membership.dto.MemberUserInfo(
        u.id, u.email, u.displayName, cast(m.role as string), cast(u.userStatus as string),
            m.isDefault, m.createdAt, m.version
        ) from TenantMembership m join AppUser u on u.id = m.id.userId
            where m.id.tenantId = :tenantId and m.id.userId = :userId
    """)
    Optional<MemberUserInfo> findMember(@Param("tenantId") UUID tenantId, @Param("userId") UUID userId);

    @Query(value = """
    select new com.example.multiapp.membership.dto.MemberUserInfo(
        u.id, u.email, u.displayName, cast(m.role as string ), cast(u.userStatus as string),
            m.isDefault, m.createdAt, m.version
        ) from TenantMembership m join AppUser u on u.id = m.id.userId
            where m.id.tenantId = :tenantId
                and (:#{#role == null} = true or m.role = :role)
    """, countQuery = """
    select count(m) from TenantMembership m where m.id.tenantId = :tenantId
        and (:#{#role == null} = true or m.role = :role)
    """)
    Page<MemberUserInfo> listMembers(@Param("tenantId") UUID tenantId, @Param("role") MembershipRole role,
                                     Pageable pageable);

    @Query(value = """
    select new com.example.multiapp.membership.dto.MemberUserInfo(
        u.id, u.email, u.displayName, cast(m.role as string), cast(u.userStatus as string),
            m.isDefault, m.createdAt, m.version
        ) from TenantMembership m join AppUser u on u.id = m.id.userId
            where m.id.tenantId = :tenantId 
                and (:#{#role == null} = true or m.role = :role)
                    and (lower(u.email) like
                :q or lower(u.displayName) like :q)
    """, countQuery = """
    select count(m)
        from TenantMembership m join AppUser u on u.id = m.id.userId
            where m.id.tenantId = :tenantId and
                (:#{#role == null} = true or m.role = :role)
                    and (lower(u.email) like :q or
                lower(u.displayName) like :q)
    """)
    Page<MemberUserInfo> searchMembers(@Param("tenantId") UUID tenantId,
                                       @Param("role") MembershipRole role,
                                       @Param("q") String q,
                                       Pageable pageable);


    List<TenantMembership> findByIdUserIdOrderByIdTenantId(UUID userId);
    @Query("""
    select new com.example.multiapp.user.dto.MeTenantResponse(
        t.id, t.name, cast(m.role as string), m.isDefault
        ) from TenantMembership m join Tenant t on t.id = m.id.tenantId
            where m.id.userId = :userId order by m.isDefault desc, t.name asc
    """)
    List<MeTenantResponse> findMyTenants(@Param("userId") UUID userId);

    @Query("""
    select new com.example.multiapp.user.dto.MeTenantResponse(
        t.id, t.name, cast(m.role as string), m.isDefault
        ) from TenantMembership m join Tenant t on t.id = m.id.tenantId
            where m.id.userId = :userId and m.isDefault = true
    """)
    Optional<MeTenantResponse> findMyDefaultTenant(@Param("userId") UUID userId);

    @Query("""
    select m from TenantMembership m where m.id.userId = :userId and m.isDefault = true
    """)
    Optional<TenantMembership> findDefaultTenant(@Param("userId") UUID userId);
}
