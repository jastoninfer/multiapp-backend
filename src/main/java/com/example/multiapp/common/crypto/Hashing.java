package com.example.multiapp.common.crypto;

import java.awt.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

public class Hashing {

    private static final HexFormat HEX = HexFormat.of();

    public static String sha256Hex(String s) {
        Objects.requireNonNull(s, "input");
        final MessageDigest md;
        try {
            md = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
        byte[] digest = md.digest(s.getBytes(StandardCharsets.UTF_8));
        return HEX.formatHex(digest);
    }
}
