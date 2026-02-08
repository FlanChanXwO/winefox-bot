package com.github.winefoxbot.plugins.gscore.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * @author FlanChan (badapple495@outlook.com)
 * @since 2026-02-08-7:42
 */
@Data
@Component
@ConfigurationProperties(prefix = "winefoxbot.plugins.gsuid")
public class GsuidPluginConfig {
    private String botId = "ShiroBot";
    private String host = "localhost";
    private String port = "8765";
    private String wsToken = "";
    private List<Long> superUsers = new ArrayList<>();
    private Boolean enable = false;
}