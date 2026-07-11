package com.zzy.finsight.auth;

public record TokenClaims(long userId, String username, String role, long expiresAtEpochSecond) {
}
