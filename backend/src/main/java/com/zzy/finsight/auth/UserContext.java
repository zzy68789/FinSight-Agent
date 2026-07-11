package com.zzy.finsight.auth;

import org.springframework.stereotype.Component;

@Component
public class UserContext {
    private static final ThreadLocal<AuthenticatedUser> CURRENT = new ThreadLocal<>();

    public void set(AuthenticatedUser user) {
        CURRENT.set(user);
    }

    public AuthenticatedUser currentUser() {
        AuthenticatedUser user = CURRENT.get();
        if (user == null) {
            throw new AuthException("未登录");
        }
        return user;
    }

    public long currentUserId() {
        return currentUser().userId();
    }

    public String currentUserRole() {
        return currentUser().role();
    }

    public void clear() {
        CURRENT.remove();
    }
}
