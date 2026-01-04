package com.github.winefoxbot.core.config.app;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @author FlanChan (badapple495@outlook.com)
 * @since 2025-12-08-21:12
 */
@Data
@ConfigurationProperties(prefix = "winefox.app")
public class WineFoxBotAppProperties {
    /**
     * 当前版本号
     */
    private String version = "latest";
}