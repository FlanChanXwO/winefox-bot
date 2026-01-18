package com.github.winefoxbot.core.config.app;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @author FlanChan (badapple495@outlook.com)
 * @since 2026-01-18-21:47
 */
@Data
@ConfigurationProperties(prefix = "winefoxbot.data")
public class WineFoxBotDataProperties {

}