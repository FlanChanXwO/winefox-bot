package com.github.winefoxbot.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

/**
 * @author FlanChan (badapple495@outlook.com)
 * @since 2025-12-08-21:12
 */
@Configuration
@Data
@ConfigurationProperties(prefix = "winefox")
public class WineFoxBotConfig {
    /**
     * 机器人主人QQ号
     */
    private List<Long> superusers = new ArrayList<>();
    /**
     * 机器人昵称QQ号
     */
    private List<Long> bot = new ArrayList<>();


    public final static String COMMAND_PREFIX_REGEX = "^/";

    public final static String COMMAND_SUFFIX_REGEX = "$";

}