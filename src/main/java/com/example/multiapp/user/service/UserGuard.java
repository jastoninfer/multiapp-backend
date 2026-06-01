package com.example.multiapp.user.service;

import com.example.multiapp.common.api.ForbiddenException;
import com.example.multiapp.common.api.NotFoundException;
import com.example.multiapp.user.entity.AppUser;
import com.example.multiapp.user.model.UserStatus;
import com.example.multiapp.user.repo.AppUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.UUID;

@RequiredArgsConstructor
@Service
public class UserGuard {
    private final AppUserRepository userRepo;

    @Transactional(readOnly = true)
    public void requireActiveUser(UUID userId) {
        if(userId == null) return;
        AppUser user = userRepo.findById(userId).orElseThrow(() ->
                new NotFoundException("user not found: %s".formatted(userId)));
        requireActiveUser(user);
    }

    public void requireActiveUser(AppUser user) {
        if(user == null) return;
//        Objects.requireNonNull(user, "user");
        if(user.getUserStatus() != UserStatus.ACTIVE) {
            throw new ForbiddenException("User is disabled");
        }
    }
}
