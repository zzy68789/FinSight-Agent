package com.zzy.finsight.auth;

import com.zzy.finsight.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 校验请求令牌并建立当前用户上下文。
 */
@Component
public class AuthInterceptor implements HandlerInterceptor {
    private final AuthService authService;
    private final UserContext userContext;
    private final boolean enabled;

    public AuthInterceptor(AuthService authService, UserContext userContext, @Value("${finsight.auth.enabled:true}") boolean enabled) {
        this.authService = authService;
        this.userContext = userContext;
        this.enabled = enabled;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if (!enabled || isPublic(request)) {
            return true;
        }
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Missing bearer token");
            return false;
        }
        try {
            userContext.set(authService.resolveToken(header.substring("Bearer ".length())));
            return true;
        } catch (AuthException e) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, e.getMessage());
            return false;
        }
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        userContext.clear();
    }

    private boolean isPublic(HttpServletRequest request) {
        String path = request.getRequestURI();
        return "OPTIONS".equalsIgnoreCase(request.getMethod())
                || "/".equals(path)
                || "/api/auth/login".equals(path)
                || "/api/auth/register".equals(path);
    }
}
