package com.github.winefoxbot.core.config.inner;

import com.github.winefoxbot.core.aop.interceptor.WebUIAuthInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

@Configuration
@RequiredArgsConstructor
public class WebMvcCorsConfig implements WebMvcConfigurer {
    private final WebUIAuthInterceptor authInterceptor;

    // 无需登录的白名单路径
    private static final List<String> WHITELIST = List.of(
            "/api/login",
            "/api/reset-password",
            "/api/check/ping"
    );

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**") // 对所有路径生效
                .allowedOriginPatterns("*")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH") // 允许的方法
                .allowedHeaders("Authorization", "Content-Type", "Accept", "*")
                .exposedHeaders("Authorization")
                .allowCredentials(true) // 是否允许携带 Cookie 等凭证
                .maxAge(3600); // 预检请求（OPTIONS）的缓存时间（秒）
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authInterceptor)
                .addPathPatterns("/api/**") // 拦截所有 /api 开头的路径
                .excludePathPatterns(WHITELIST); // 排除白名单
    }
}
