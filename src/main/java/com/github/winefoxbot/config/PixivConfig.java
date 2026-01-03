package com.github.winefoxbot.config;

import jakarta.annotation.PostConstruct;
import lombok.Data;
import okhttp3.Headers;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;

/**
 * @author FlanChan (badapple495@outlook.com)
 * @since 2025-12-09-15:33
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "pixiv")
public class PixivConfig {
    /**
     * Pixiv Cookie
     */
    private String cookie = "";

    private String pAbId = "";

    private String phpSessId = "";

    /**
     * Pixiv请求头
     */
    private Headers headers = Headers.of();
    @PostConstruct
    public void init() {
        headers = Headers.of(
                HttpHeaders.REFERER, "https://www.pixiv.net",
                HttpHeaders.USER_AGENT, "Mozilla/5.0 (Windows NT 10.0; WOW64) AppleWebKit/537.36 Chrome/80.0.3987.163 Safari/537.36",
                HttpHeaders.COOKIE, cookie
        );
    }
}