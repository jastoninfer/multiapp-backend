package com.example.multiapp.user.repo;

import com.example.multiapp.user.entity.AppUser;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface AppUserRepository extends JpaRepository<AppUser, UUID> {
    Optional<AppUser> findByIssuerAndKeycloakSub(String issuer, String keycloakSub);
    boolean existsAppUserById(UUID id);
    // 传入前调用strip().toLowerCase();
    Optional<AppUser> findByEmail(String email);
}
