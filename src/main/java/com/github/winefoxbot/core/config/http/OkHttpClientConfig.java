package com.github.winefoxbot.core.config.http;

import com.github.winefoxbot.core.config.http.interceptor.RetryInterceptor;
import jakarta.annotation.PostConstruct;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import okhttp3.ConnectionPool;
import okhttp3.OkHttpClient;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.net.ssl.*;
import java.net.ProxySelector;
import java.util.concurrent.TimeUnit;

/**
 * @author FlanChan
 */
@Data
@Slf4j
@Configuration
@ConfigurationProperties(prefix = "request")
public class OkHttpClientConfig {

    private ProxyConfig proxyConfig;

    // 1. 定义选择器 Bean
    @Bean
    public AutoSwitchProxySelector autoSwitchProxySelector(ProxyConfig proxyConfig) {
        this.proxyConfig = proxyConfig;
        return new AutoSwitchProxySelector(proxyConfig);
    }

    // 2. 初始化时，接管整个 JVM 的代理逻辑
    @PostConstruct
    public void initGlobalProxySelector() {
        if (proxyConfig != null) {
            AutoSwitchProxySelector selector = new AutoSwitchProxySelector(proxyConfig);
            ProxySelector.setDefault(selector);
            log.warn("已设置 JVM 全局 ProxySelector (AutoSwitchProxySelector)，接管所有网络流量路由");
        }
    }

    @Bean
    public OkHttpClient okHttpClient(AutoSwitchProxySelector proxySelector) {
        try {
            ConnectionPool connectionPool = new ConnectionPool(20, 5, TimeUnit.MINUTES);
            return new OkHttpClient.Builder()
                    .connectTimeout(10, TimeUnit.SECONDS)
                    .retryOnConnectionFailure(true)
                    .connectionPool(connectionPool)
                    .followRedirects(false)
                    .followSslRedirects(false)
                    .addInterceptor(new RetryInterceptor(3, 1000))
                    .proxySelector(proxySelector)
                    .build();

        } catch (Exception e) {
            log.error("创建 OkHttpClient 失败", e);
            throw new RuntimeException(e);
        }
    }
}
