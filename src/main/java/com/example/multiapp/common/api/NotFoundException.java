package com.example.multiapp.common.api;

public class NotFoundException extends RuntimeException{
    public NotFoundException(String m) {
        super(m);
    }
}
