package com.example.multiapp.common.api;

public class ForbiddenException extends RuntimeException {
    public ForbiddenException(String m) {
        super(m);
    }
}
