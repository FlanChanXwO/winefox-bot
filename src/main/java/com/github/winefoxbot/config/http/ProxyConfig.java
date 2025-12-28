package com.github.winefoxbot.config.http;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

/**
 * @author FlanChan (badapple495@outlook.com)
 * @since 2025-12-09-15:46
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "okhttp.proxy")
public class ProxyConfig {
    /**
     * 代理主机地址
     */
    private String host = "127.0.0.1";
    /**
     * 代理端口
     */
    private int port = 7890;
    /**
     * 代理是否开启
     */
    private Boolean enabled = false;
    /**
     * 代理类型
     */
    private ProxyType type = ProxyType.SOCKS5;

    public enum ProxyType {
        HTTP, SOCKS5
    }

    private List<String> noProxyHosts = new ArrayList<>();
}