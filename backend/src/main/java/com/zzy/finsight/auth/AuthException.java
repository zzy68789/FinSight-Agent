package com.zzy.finsight.auth;

/**
 * 表示身份认证过程中的业务异常。
 */
public class AuthException extends RuntimeException {
    public AuthException(String message) {
        super(message);
    }
}
