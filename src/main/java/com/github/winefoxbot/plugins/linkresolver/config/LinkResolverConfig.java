package com.github.winefoxbot.plugins.linkresolver.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * 插件静态配置类
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "winefoxbot.plugins.linkresolver")
public class LinkResolverConfig {
    /**
     * 临时数据路径
     */
    private String tmpPath = "data/linkresolver";
    /**
     * 重复解析时间
     */
    private Duration reanalysisTimeSeconds = Duration.ofSeconds(60);
}