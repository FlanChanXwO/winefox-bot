package com.github.winefoxbot.config;

import jakarta.annotation.PostConstruct;
import lombok.Data;
import okhttp3.Headers;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;

import java.io.File;
import java.util.List;

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
    /**
     * 图片存储根目录
     */
    private String imgRoot = "pixiv-images/";
    /**
     * 图片压缩包存储根目录
     */
    private String imgZipRoot = "pixiv-zip/";
    /**
     * 是否启用Pixiv R18内容
     */
    private boolean enableR18 = true;
    /**
     * 禁用R18的群ID列表
     */
    private List<Long> banR18Groups;
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
        new File(imgRoot).mkdirs();
        new File(imgZipRoot).mkdirs();
    }
}