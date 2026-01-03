package com.github.winefoxbot.config;


import com.github.winefoxbot.config.http.ProxyConfig;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.options.Proxy;
import jakarta.annotation.PostConstruct;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Paths;
import java.util.List;

/**
 * @author FlanChan (badapple495@outlook.com)
 * @since 2025-12-24-12:47
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "webdriver")
public class WebDriverConfig {
    private String edgeDriverPath = "path/to/edgeBinaryPath";

    private String edgeBinaryPath = "path/to/edge/binary";

    @PostConstruct
    public void init() {
        // 设置 WebDriver 的路径
        System.setProperty("webdriver.edge.driver",edgeDriverPath);
        System.setProperty("playwright.skipBrowserDownload", "true");
    }

    @Bean(destroyMethod = "close")
    public Playwright playwright() {
        return Playwright.create();
    }


    @Bean
    public Proxy playwrightProxy(ProxyConfig proxyConfig) {
        if (proxyConfig.getEnabled()) {
            return new Proxy(proxyConfig.getType().name().toUpperCase() + "://" +proxyConfig.getHost() + ":" + proxyConfig.getPort());
        }
        return null;
    }

    @Bean(destroyMethod = "close")
    public Browser browser(Playwright playwright, Proxy proxy) {
        return playwright.chromium().launch(
                new BrowserType.LaunchOptions()
                        .setProxy(proxy)
                        .setArgs(List.of("--no-sandbox", "--disable-setuid-sandbox"))
                        .setExecutablePath(Paths.get(edgeBinaryPath))
                        .setHeadless(true)
        );
    }
}