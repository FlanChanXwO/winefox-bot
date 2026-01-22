package com.github.winefoxbot.core.config.request;

import com.github.winefoxbot.core.config.request.interceptor.RetryInterceptor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import okhttp3.ConnectionPool;
import okhttp3.Dispatcher;
import okhttp3.OkHttpClient;
import okhttp3.brotli.BrotliInterceptor;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

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

    @Bean
    public AutoSwitchProxySelector autoSwitchProxySelector(ProxyConfig proxyConfig) {
        this.proxyConfig = proxyConfig;
        return new AutoSwitchProxySelector(proxyConfig);
    }

    @Bean
    public OkHttpClient okHttpClient(AutoSwitchProxySelector proxySelector) {
        try {
            Dispatcher dispatcher = new Dispatcher();
            dispatcher.setMaxRequests(500);
            dispatcher.setMaxRequestsPerHost(100);
            ConnectionPool connectionPool = new ConnectionPool(50, 5, TimeUnit.MINUTES);
            return new OkHttpClient.Builder()
                    .connectTimeout(10, TimeUnit.SECONDS)
                    .readTimeout(30, TimeUnit.SECONDS)
                    .retryOnConnectionFailure(true)
                    .connectionPool(connectionPool)
                    .dispatcher(dispatcher)
                    .followRedirects(true)
                    .followSslRedirects(true)
                    .addInterceptor(BrotliInterceptor.INSTANCE)
                    .addInterceptor(new RetryInterceptor(3, 1000))
                    .proxySelector(proxySelector)
                    .build();

        } catch (Exception e) {
            log.error("创建 OkHttpClient 失败", e);
            throw new RuntimeException(e);
        }
    }
}
