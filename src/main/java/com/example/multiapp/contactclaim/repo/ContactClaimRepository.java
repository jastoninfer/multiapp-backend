package com.example.multiapp.contactclaim.repo;

import com.example.multiapp.contact.entity.Contact;
import com.example.multiapp.contactclaim.entity.ContactClaim;
import com.example.multiapp.contactclaim.entity.ContactClaimId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

/*
* 用native query拿可用claim并锁行, 避免并发两次消费同一个code
* */
public interface ContactClaimRepository extends JpaRepository<ContactClaim, ContactClaimId> {
    // 通过code_hash找到可用claim并For update锁住这一行
    @Query(value = """
    select * from app.contact_claim
        where code_hash = :codeHash
            and consumed_at is null
            and expires_at > CURRENT_TIMESTAMP
        limit 1
        for update
    """, nativeQuery = true)
    Optional<ContactClaim> findActiveByCodeHashForUpdate(
            @Param("codeHash") String codeHash);



    // 给某contact查是否有未消费的有效claim(可选)
    @Query(value = """
    select * from app.contact_claim
        where tenant_id = :tenantId
            and contact_id = :contactId
            and consumed_at is null
            and expires_at > CURRENT_TIMESTAMP
        limit 1
    """, nativeQuery = true)
    Optional<ContactClaim> findLatestActiveByContact(
            @Param("tenantId") UUID tenantId,
            @Param("contactId")UUID contactId);
}
