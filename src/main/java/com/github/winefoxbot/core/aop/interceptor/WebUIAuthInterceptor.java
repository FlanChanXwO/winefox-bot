package com.github.winefoxbot.core.aop.interceptor;

import com.github.winefoxbot.core.service.webui.WebUITokenService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * WebUI 认证拦截器
 * @since 2026-01-18
 */
@Component
@RequiredArgsConstructor
public class WebUIAuthInterceptor implements HandlerInterceptor {

    private final WebUITokenService tokenService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 如果是 OPTIONS 请求（跨域预检），直接放行
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        var token = request.getHeader("Authorization");

        if (token == null) {
            return false;
        }

        if (token.startsWith("Bearer ")) {
            token = token.substring(7);
        }

        if (tokenService.validateToken(token)) {
            return true;
        }

        // 认证失败，返回 401
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"code\": 401, \"message\": \"未登录或 Token 已过期\"}");
        return false;
    }
}
