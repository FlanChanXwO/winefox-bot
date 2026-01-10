package com.github.winefoxbot.core.config.playwright;

import com.github.winefoxbot.core.config.http.ProxyConfig;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.options.Proxy;
import jakarta.annotation.PostConstruct;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

import java.nio.file.Paths;
import java.util.List;

/**
 * Playwright 核心配置类
 * <p>
 * 管理浏览器实例的生命周期及全局配置参数
 *
 * @author FlanChan
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "playwright")
public class PlaywrightConfig {

    /**
     * 浏览器选择策略
     */
    private BrowserStrategy type = BrowserStrategy.EDGE;

    /**
     * 自定义浏览器可执行文件路径
     * 仅当 type = CUSTOM 时生效且必填
     */
    private String browserBinaryPath;

    /**
     * 是否以无头模式运行
     */
    private boolean headless = true;

    /**
     * 全局设备像素缩放因子
     * (用于业务代码创建 Context 时引用)
     */
    private double deviceScaleFactor = 1.0;

    public enum BrowserStrategy {
        /** 使用系统安装的 Google Chrome */
        CHROME,
        /** 使用系统安装的 Microsoft Edge */
        EDGE,
        /** 使用指定路径的浏览器 */
        CUSTOM
    }

    @PostConstruct
    public void init() {
        // 因为我们明确指定了 Channel 或 Path，Playwright 不需要下载自带的 Chromium
        System.setProperty("playwright.skipBrowserDownload", "true");
    }

    @Bean(destroyMethod = "close")
    public Playwright playwright() {
        return Playwright.create();
    }

    @Bean
    public Proxy playwrightProxy(ProxyConfig proxyConfig) {
        if (proxyConfig != null && Boolean.TRUE.equals(proxyConfig.getEnabled())) {
            return new Proxy(proxyConfig.getType().name().toUpperCase() + "://" + proxyConfig.getHost() + ":" + proxyConfig.getPort());
        }
        return null;
    }

    @Bean(destroyMethod = "close")
    public Browser browser(Playwright playwright, Proxy proxy) {
        BrowserType.LaunchOptions options = new BrowserType.LaunchOptions()
                .setProxy(proxy)
                .setArgs(List.of("--no-sandbox", "--disable-setuid-sandbox"))
                .setHeadless(this.headless);

        switch (type) {
            case CHROME:
                options.setChannel("chrome");
                break;
            case EDGE:
                options.setChannel("msedge");
                break;
            case CUSTOM:
                if (!StringUtils.hasText(browserBinaryPath)) {
                    throw new IllegalArgumentException("配置错误：当 playwright.type = CUSTOM 时，playwright.browser-binary-path 不能为空！");
                }
                options.setExecutablePath(Paths.get(browserBinaryPath));
                break;
            default:
                // 默认回退到 Edge
                options.setChannel("msedge");
        }

        // Edge 和 Chrome 均属于 Chromium 内核
        return playwright.chromium().launch(options);
    }
}
