package com.github.winefoxbot.config.app;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * @author FlanChan (badapple495@outlook.com)
 * @since 2025-12-08-21:12
 */
@Data
@ConfigurationProperties(prefix = "winefox")
public class WineFoxBotProperties {
    private WineFoxBotRebotProperties robot;

    private WineFoxBotAppProperties app;

    private WineFoxBotAppUpdateProperties appUpdate;
}