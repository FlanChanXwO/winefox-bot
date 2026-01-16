package com.github.winefoxbot.plugins.bittorrent.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * @author FlanChan (badapple495@outlook.com)
 * @since 2025-12-11-0:20
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "winefoxbot.plugins.bittorrent")
public class BitTorrentConfig {
    /**
     * 是否启用磁力链搜索功能
     */
    private boolean enabled = true;
    /**
     * 单次最大搜索结果数量（最高10）
     */
    private Integer maxSearchSize = 10;
    /**
     * 是否自动撤回搜索结果消息
     */
    private boolean autoRevoke = false;
    /**
     * 自动撤回延时，单位：秒
     */
    private int revokeDelaySeconds = 10;
    /**
     * 最大重定向次数
     */
    private Integer maxRedirects = 10;
    /**
     * 基础Url
     */
    private String baseUrl = "https://ginger25.top";
    /**
     * Referer 请求头
     */
    private String referer = "https://ginger25.top/";
    /**
     * Origin 请求头
     */
    private String origin = "https://ginger25.top";

}