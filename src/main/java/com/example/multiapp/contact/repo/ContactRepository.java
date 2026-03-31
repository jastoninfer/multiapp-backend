package com.example.multiapp.contact.repo;

import com.example.multiapp.contact.dto.ContactQuery;
import com.example.multiapp.contact.entity.Contact;
import com.example.multiapp.contact.entity.ContactId;
import jakarta.validation.constraints.NotNull;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.security.core.parameters.P;

import java.util.Optional;
import java.util.UUID;

public interface ContactRepository extends JpaRepository<Contact, ContactId> {

    @Query(value = """
      select c from Contact c
       where c.id.tenantId = :tenantId
         and (c.linkedUserId is not null and :#{#q.linked} = true)
         and (:#{#q.displayName} is null or lower(c.displayName)
             like lower(concat('%', :#{#q.displayName}, '%'))    escape '\\')
         and (:#{#q.email} is null or lower(c.emailNormalized)
             like lower(concat('%', :#{#q.email}, '%'))    escape '\\')
         and (:#{#q.phone} is null or c.phoneNormalized
             like concat('%', :#{#q.phone}, '%')   escape '\\')
         and (:#{#q.linked} is null
             or (:#{#q.linked} = true and c.linkedUserId is not null)
             or (:#{#q.linked} = false and c.linkedUserId is null))
    """, countQuery = """
      select count(c) from Contact c
       where c.id.tenantId = :tenantId
         and (:#{#q.displayName} is null or lower(c.displayName)
             like lower(concat('%', :#{#q.displayName}, '%'))    escape '\\')
         and (:#{#q.email} is null or lower(c.emailNormalized)
             like lower(concat('%', :#{#q.email}, '%'))    escape '\\')
         and (:#{#q.phone} is null or c.phoneNormalized
             like concat('%', :#{#q.phone}, '%')   escape '\\')
         and (:#{#q.linked} is null
             or (:#{#q.linked} = true and c.linkedUserId is not null)
             or (:#{#q.linked} = false and c.linkedUserId is null))
    """)
    Page<Contact> searchContacts(
            @Param("tenantId") UUID tenantId,
            @Param("q")ContactQuery q,
            Pageable pageable);

    @Query(value = """
    select * from app.contact
        where tenant_id = :tenantId
            and id = :id
        limit 1
        for update
    """, nativeQuery = true)
    Optional<Contact> findByIdTenantIdAndIdIdForUpdate(UUID tenantId, UUID id);
    boolean existsByIdTenantIdAndIdId(UUID tenantId, UUID id);
    Optional<Contact> findByIdTenantIdAndIdId(UUID tenantId, UUID id);
    boolean existsByIdTenantIdAndIdIdAndLinkedUserId(UUID tenantId, UUID id, UUID linkedUserId);
}
