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
    /**
     * 机器人主人QQ号
     */
    private List<Long> superusers = new ArrayList<>();
    /**
     * 机器人昵称QQ号
     */
    private List<Long> bot = new ArrayList<>();
    /**
     * 当前版本号
     */
    private String version = "latest";

    private final WineFoxBotAppProperties app;

}