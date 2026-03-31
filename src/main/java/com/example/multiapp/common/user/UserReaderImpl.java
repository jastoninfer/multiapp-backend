package com.example.multiapp.common.user;

import com.example.multiapp.user.entity.AppUser;
import com.example.multiapp.user.repo.AppUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class UserReaderImpl implements UserReader{
    private final AppUserRepository userRepo;

    @Override
    @Transactional(readOnly = true)
    public boolean existsUser(UUID userId) {
        Objects.requireNonNull(userId, "userId");
        return userRepo.existsAppUserById(userId);
    }
}
