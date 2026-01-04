package com.github.winefoxbot.config.app;

import lombok.Data;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * @author FlanChan (badapple495@outlook.com)
 * @since 2025-12-08-21:12
 */
@Configuration
@Data
@Import({WineFoxBotProperties.class,WineFoxBotAppProperties.class,WineFoxBotAppUpdateProperties.class})
public class WineFoxBotConfig {

    public final static String COMMAND_PREFIX = "/";

    public final static String COMMAND_PREFIX_REGEX = "^" + COMMAND_PREFIX;

    public final static String COMMAND_SUFFIX = "";

    public final static String COMMAND_SUFFIX_REGEX = COMMAND_SUFFIX + "$";
}