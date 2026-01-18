package com.github.winefoxbot.core.config.inner;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcCorsConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**") // 对所有路径生效
                // .allowedOrigins("*") // ⚠️注意：Spring Boot 2.4+ 且 allowCredentials 为 true 时，不能用 *
                .allowedOriginPatterns("*") // Spring Boot 2.4+ 推荐使用这个代替 allowedOrigins("*")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH") // 允许的方法
                .allowedHeaders("*") // 允许的请求头
                .allowCredentials(true) // 是否允许携带 Cookie 等凭证
                .maxAge(3600); // 预检请求（OPTIONS）的缓存时间（秒）
    }
}
