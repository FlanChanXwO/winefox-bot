package com.github.winefoxbot.core.config.webui;

import com.github.winefoxbot.core.model.entity.WebUIAdmin;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.sisu.PostConstruct;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.time.Duration;

/**
 * @author FlanChan (badapple495@outlook.com)
 * @since 2026-01-14-17:17
 */
@Configuration
@EnableConfigurationProperties(WebUIProperties.class)
@Slf4j
@RequiredArgsConstructor
public class WebUIConfig implements WebMvcConfigurer {
    private final WebUIProperties properties;

    @EventListener(ApplicationReadyEvent.class)
    public void init() {
        WebUIAdmin admin = properties.getAdmin();
        String recoveryCode = properties.getRecoveryCode();
        log.info("webui 配置初始化完成");
        log.info("当前管理员用户名: {}", admin.getUsername());
        log.info("密码恢复码: {}", recoveryCode);
    }

}