package com.example.multiapp.user.entity;

import com.example.multiapp.common.jpa.AuditedEntity;
import com.example.multiapp.user.model.UserStatus;
import jakarta.annotation.Nullable;
import jakarta.persistence.*;
import lombok.*;

import java.util.Objects;
import java.util.UUID;

@Entity
@Table(
        name = "app_user",
        schema = "app",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_user_issuer_sub",
                columnNames = {"issuer", "keycloak_sub"}
        )
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
@EqualsAndHashCode(onlyExplicitlyIncluded = true, callSuper = false)
@ToString(onlyExplicitlyIncluded = true)
public class AppUser extends AuditedEntity {
    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "issuer", nullable = false)
    @EqualsAndHashCode.Include
    private String issuer;

    @Column(name = "keycloak_sub", nullable = false)
    @EqualsAndHashCode.Include
    private String keycloakSub;

    @Column(nullable = false)
    @ToString.Include
    private String email;

    @Column(name = "display_name", nullable = false)
    private String displayName;

    @Column
    @ToString.Include
    private String phone;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    @ToString.Include
    private UserStatus userStatus;

    @Column(name = "is_platform_admin", nullable = false)
    @ToString.Include
    private boolean isPlatformAdmin;

    public static AppUser create(String issuer, String sub, String email,
                                 String displayName, @Nullable String phone, @Nullable boolean isPlatformAdmin) {
        if(issuer == null || issuer.isBlank())
            throw new IllegalArgumentException("issuer cannot be blank");
        if(sub == null || sub.isBlank())
            throw new IllegalArgumentException("sub cannot be blank");
        if(email == null || email.isBlank())
            throw new IllegalArgumentException("email cannot be blank");
        if(displayName == null || displayName.isBlank())
            throw new IllegalArgumentException("display name cannot be blank");
        return new AppUser(issuer, sub, email, displayName, phone, isPlatformAdmin);
    }

    private AppUser(String issuer, String sub, String email,
                    String displayName, @Nullable String phone, boolean isPlatformAdmin) {
        this.id = UUID.randomUUID();
        this.issuer = issuer.strip();
        this.keycloakSub = sub.strip();
        this.email = email.strip().toLowerCase();
        this.displayName = displayName.strip();
        this.phone = (phone == null || phone.isBlank()) ? null : phone.strip();
        this.userStatus = UserStatus.ACTIVE;
        this.isPlatformAdmin = isPlatformAdmin;
    }

    public void transitionTo(UserStatus toStatus) {
        this.userStatus = Objects.requireNonNull(toStatus, "toStatus");
    }

    public void disable() {
        this.userStatus = UserStatus.DISABLED;
    }

    public void enable() {
        this.userStatus = UserStatus.ACTIVE;
    }

    public void updateProfile(String displayName, String phone) {
        if (displayName != null) {
            String dn = displayName.strip();
            if(dn.isEmpty()) throw new IllegalArgumentException("display name cannot be blank");
            this.displayName = displayName;
        }
        this.phone = (phone == null || phone.isBlank()) ? null : phone.strip();
    }

}
