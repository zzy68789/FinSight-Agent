package com.zzy.finsight.service;

import com.zzy.finsight.auth.AuthenticatedUser;

public interface AuthService {
    AuthenticatedUser register(String username, String email, String password);

    AuthenticatedUser login(String username, String password);

    AuthenticatedUser resolveToken(String token);
}
