package com.example.multiapp.common.api;

public class PreconditionFailedException extends RuntimeException{
    public PreconditionFailedException(String m) { super(m);}
}