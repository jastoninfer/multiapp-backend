package com.example.multiapp.tenant.entity;

import com.example.multiapp.common.jpa.AuditedEntity;
import com.example.multiapp.tenant.dto.TenantResponse;
import com.example.multiapp.tenant.model.TenantStatus;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.*;

import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

@Entity
@ToString(onlyExplicitlyIncluded = true)
@EqualsAndHashCode(onlyExplicitlyIncluded = true, callSuper = false)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "tenant", schema = "app")
public class Tenant extends AuditedEntity {
    @Id
    private UUID id;

    @ToString.Include
    @EqualsAndHashCode.Include
    @Column(name = "name", nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private TenantStatus status;

    public static Tenant create(@NotBlank String name) {
        requireNonBlank(name, "tenant name");
        Tenant tenant = new Tenant();
        tenant.id = UUID.randomUUID();
        tenant.name = name.strip();
        tenant.status = TenantStatus.ACTIVE;
        return tenant;
    }

    public void transitionTo(TenantStatus to) {
        this.status = Objects.requireNonNull(to, "tenant status must not be null");
    }

    public void updateName(String newName) {
        this.name = newName.strip();
    }


    private static void requireNonBlank(String s, String name) {
        if (s == null || s.isBlank()) throw new IllegalArgumentException(name + " must be non-blank");
    }
}
