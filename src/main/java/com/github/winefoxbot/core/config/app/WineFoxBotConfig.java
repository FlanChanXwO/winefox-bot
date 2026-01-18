package com.github.winefoxbot.core.config.app;

import lombok.Data;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * @author FlanChan (badapple495@outlook.com)
 * @since 2025-12-08-21:12
 */
@Configuration
@Data
@EnableConfigurationProperties({WineFoxBotProperties.class, WineFoxBotRobotProperties.class,WineFoxBotAppProperties.class,WineFoxBotAppUpdateProperties.class, WineFoxBotDataProperties.class})
public class WineFoxBotConfig {

    public final static String COMMAND_PREFIX = "/";

    public final static String COMMAND_PREFIX_REGEX = "^" + COMMAND_PREFIX;

    public final static String COMMAND_SUFFIX = "";

    public final static String COMMAND_SUFFIX_REGEX = COMMAND_SUFFIX;
}