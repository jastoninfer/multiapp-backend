package com.example.multiapp.common.user;

import com.example.multiapp.user.entity.AppUser;

import java.util.Optional;
import java.util.UUID;

public interface UserReader {
    public boolean existsUser(UUID userId);
}
