package com.github.winefoxbot.core.config.webui;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @author FlanChan (badapple495@outlook.com)
 * @since 2026-01-14-17:17
 */
@Data
@ConfigurationProperties(prefix = "winefoxbot.webui")
public class WebUIProperties {
    private Boolean enable = true;
}