package com.example.multiapp.membership.entity;

import com.example.multiapp.common.jpa.CreatedOnlyEntity;
import com.example.multiapp.membership.model.MembershipRole;
import jakarta.persistence.*;
import lombok.*;

import java.util.Objects;
import java.util.UUID;

@Getter
@ToString(onlyExplicitlyIncluded = true)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EqualsAndHashCode(onlyExplicitlyIncluded = true, callSuper = false)
@Entity
@Table(name="tenant_membership", schema = "app")
public class TenantMembership extends CreatedOnlyEntity {
    @EmbeddedId
    @EqualsAndHashCode.Include
    @ToString.Include
    private TenantMembershipId id;

    @Version
    @Column(name="version", nullable = false)
    private long version;

    @Enumerated(EnumType.STRING)
    @Column(name="role", nullable = false)
    @ToString.Include
    private MembershipRole role;

    @ToString.Include
    @Column(name="is_default", nullable = false)
    private boolean isDefault;

    private TenantMembership(TenantMembershipId id, MembershipRole role, boolean isDefault){
        this.id = Objects.requireNonNull(id);
        this.role = Objects.requireNonNull(role);
        this.isDefault = isDefault;
    }

    public static TenantMembership create(UUID tenantId, UUID userId, MembershipRole role, boolean isDefault) {
        return new TenantMembership(new TenantMembershipId(tenantId, userId), role, isDefault);
    }

    public void changeRole(MembershipRole newRole){
        this.role = Objects.requireNonNull(newRole);
    }

    public void markDefault(){
        this.isDefault = true;
    }

    public void unmarkDefault(){
        this.isDefault = false;
    }
}
