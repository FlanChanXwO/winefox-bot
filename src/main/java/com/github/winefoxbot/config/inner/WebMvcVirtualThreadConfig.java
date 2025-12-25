package com.github.winefoxbot.config.inner;

import org.springframework.boot.web.embedded.tomcat.TomcatProtocolHandlerCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.Executors;

@Configuration
public class WebMvcVirtualThreadConfig {
    // 配置 Tomcat 使用虚拟线程处理请求
    @Bean
    public TomcatProtocolHandlerCustomizer<?> protocolHandlerVirtualThreadExecutorCustomizer() {
        // 让 Tomcat 使用虚拟线程处理请求，性能提升明显
        // 每个 HTTP 请求都在独立的虚拟线程上处理，不受线程数量限制
        return protocolHandler -> {
            protocolHandler.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        };
    }
}
