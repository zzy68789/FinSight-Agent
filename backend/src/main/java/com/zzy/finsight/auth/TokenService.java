package com.zzy.finsight.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;

/**
 * 签发并校验项目内部访问令牌。
 */
@Component
public class TokenService {
    private final String secret;
    private final long ttlSeconds;

    public TokenService(
            @Value("${finsight.auth.token-secret:dev-only-change-me}") String secret,
            @Value("${finsight.auth.token-ttl-seconds:86400}") long ttlSeconds
    ) {
        this.secret = secret;
        this.ttlSeconds = ttlSeconds;
    }

    public String issue(long userId, String username, String role) {
        long expiresAt = Instant.now().getEpochSecond() + ttlSeconds;
        String payload = userId + "|" + username + "|" + role + "|" + expiresAt;
        String encodedPayload = base64Url(payload.getBytes(StandardCharsets.UTF_8));
        return encodedPayload + "." + sign(encodedPayload);
    }

    public TokenClaims verify(String token) {
        if (token == null || token.isBlank() || !token.contains(".")) {
            throw new AuthException("无效 token");
        }
        String[] parts = token.split("\\.", 2);
        String expectedSignature = sign(parts[0]);
        if (!java.security.MessageDigest.isEqual(expectedSignature.getBytes(StandardCharsets.UTF_8), parts[1].getBytes(StandardCharsets.UTF_8))) {
            throw new AuthException("token 签名无效");
        }
        String payload = new String(Base64.getUrlDecoder().decode(parts[0]), StandardCharsets.UTF_8);
        String[] values = payload.split("\\|");
        if (values.length != 4) {
            throw new AuthException("token 内容无效");
        }
        long expiresAt = Long.parseLong(values[3]);
        if (expiresAt < Instant.now().getEpochSecond()) {
            throw new AuthException("token 已过期");
        }
        return new TokenClaims(Long.parseLong(values[0]), values[1], values[2], expiresAt);
    }

    private String sign(String encodedPayload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return base64Url(mac.doFinal(encodedPayload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("生成 token 签名失败", e);
        }
    }

    private String base64Url(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
