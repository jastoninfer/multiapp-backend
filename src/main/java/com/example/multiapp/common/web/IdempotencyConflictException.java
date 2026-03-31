package com.example.multiapp.common.web;

public class IdempotencyConflictException extends RuntimeException{
    public IdempotencyConflictException(String message) {
        super(message);
    }
}
