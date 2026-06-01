package com.example.multiapp.common.web;

import com.example.multiapp.common.api.PreconditionFailedException;

import java.util.Objects;

public final class IfMatchPreconditions {
    private IfMatchPreconditions() {}
    public static void require(String ifMatch, long version) {
        Objects.requireNonNull(ifMatch, "ifMatch");
        String v = ifMatch.trim();
        // If-Match: *表示 "只要资源存在即可"
        if("*".equals(v)) return;
        String expected = "\"" + version + "\"";
//        System.out.println("expected: " + expected);
//        System.out.printf("v: " + v);
//        System.out.printf(expected.equals(v) ? "true" : "false");
        if(!expected.equals(v)) {
            throw new PreconditionFailedException("Etag mismatch");
        }
    }
}
