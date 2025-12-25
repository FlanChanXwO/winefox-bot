package com.github.winefoxbot.config;

import com.github.winefoxbot.init.NoneBot2InitializeExecutor;
import lombok.Data;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @author FlanChan (badapple495@outlook.com)
 * @since 2025-12-14-21:32
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "nonebot2")
public class NoneBot2Config {
    private String botPath;

    private String cmd;

    private Boolean enabled = false;

    @Bean("NoneBot2InitializeExecutor")
    @ConditionalOnBooleanProperty(name = "nonebot2.enabled", havingValue = true)
    public NoneBot2InitializeExecutor noneBot2InitializeExecutor() {
        return new NoneBot2InitializeExecutor(this);
    }

}