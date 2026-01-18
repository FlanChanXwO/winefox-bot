package com.github.winefoxbot.core.config.webui;

import com.github.winefoxbot.core.model.entity.WebUIAdmin;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * @author FlanChan (badapple495@outlook.com)
 * @since 2026-01-14-17:17
 */
@Data
@ConfigurationProperties(prefix = "winefoxbot.webui")
@EnableConfigurationProperties(WebUIAdmin.class)
public class WebUIProperties {
    private WebUIAdmin admin = new WebUIAdmin();
    private String recoveryCode;
    private String jwtSecret;
}