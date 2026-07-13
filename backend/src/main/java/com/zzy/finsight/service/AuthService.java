package com.zzy.finsight.service;

import com.zzy.finsight.auth.AuthenticatedUser;

/**
 * 定义用户注册、登录和令牌解析业务。
 */
public interface AuthService {
    /** 注册普通用户并签发访问令牌。 */
    AuthenticatedUser register(String username, String email, String password);

    /** 校验账号密码并签发访问令牌。 */
    AuthenticatedUser login(String username, String password);

    /** 解析访问令牌并返回当前用户。 */
    AuthenticatedUser resolveToken(String token);
}
