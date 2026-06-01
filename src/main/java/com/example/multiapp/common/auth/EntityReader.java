package com.example.multiapp.common.auth;

import com.example.multiapp.common.api.NotFoundException;
import jakarta.persistence.EntityNotFoundException;

public abstract class EntityReader {
    protected abstract String entityName();

    protected void notFound() {
        throw new NotFoundException(entityName() + " not found");
    }
}
