package com.github.winefoxbot.core.config.webui;

import com.github.winefoxbot.core.model.entity.WebUIAdmin;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.sisu.PostConstruct;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * @author FlanChan (badapple495@outlook.com)
 * @since 2026-01-14-17:17
 */
@Configuration
@EnableConfigurationProperties(WebUIProperties.class)
@Slf4j
@RequiredArgsConstructor
public class WebUIConfig {
    private final WebUIProperties properties;

    @PostConstruct
    public void init() {
        WebUIAdmin admin = properties.getAdmin();
        String recoveryCode = properties.getRecoveryCode();
        log.info("webui 初始化完成");
        log.info("当前管理员用户名: {}", admin.getUsername());
        log.info("密码恢复码: {}", recoveryCode);
    }
}