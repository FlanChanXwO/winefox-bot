package com.github.winefoxbot.plugins.imgexploration.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * @author FlanChan (badapple495@outlook.com)
 * @since 2026-01-16-19:57
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "winefoxbot.plugins.img-exploration")
public class ImgExplorationConfig {
    /**
     * SerpAPI API Keys
     */
    private List<String> serpApikeys;

    /**
     * SauceNAO API Key
     */
    private String sauceNaoApiKey;

    /**
     * Ascii2D Session ID，通过浏览器访问 ascii2d.net 时获取的 sessionid Cookie 值
     */
    private String ascii2dSessionId;
}