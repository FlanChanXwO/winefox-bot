package com.github.winefoxbot.config.pixiv;

import jakarta.annotation.PostConstruct;
import lombok.Data;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import okhttp3.Headers;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;

/**
 * @author FlanChan (badapple495@outlook.com)
 * @since 2025-12-09-15:33
 */
@Getter
@Configuration
@RequiredArgsConstructor
@EnableConfigurationProperties({
        PixivProperties.class,
        PixivProperties.Tracker.class,
        PixivProperties.AuthorizationProperties.class,
        PixivProperties.ApiProperties.class,
        PixivProperties.CookieProperties.class,
        PixivProperties.Bookmark.class})
public class PixivConfig {
    private final PixivProperties pixivProperties;

    /**
     * Pixiv请求头
     */
    private Headers headers = Headers.of();

    @PostConstruct
    public void init() {

        headers = Headers.of(
                HttpHeaders.REFERER, "https://www.pixiv.net",
                HttpHeaders.USER_AGENT, "Mozilla/5.0 (Windows NT 10.0; WOW64) AppleWebKit/537.36 Chrome/80.0.3987.163 Safari/537.36",
                HttpHeaders.COOKIE, "PHPSESSID=%s; p_ab_id=%s;".formatted(
                        pixivProperties.getCookie().getPhpsessid(),
                        pixivProperties.getCookie().getPAbId()
                )
        );
    }
}