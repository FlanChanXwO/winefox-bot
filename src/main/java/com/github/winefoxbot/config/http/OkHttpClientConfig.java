package com.github.winefoxbot.config.http;

import com.github.winefoxbot.config.http.interceptor.RetryInterceptor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.IOException;
import java.net.*;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * @author FlanChan (badapple495@outlook.com)
 * @since 2025-12-09-15:44
 */
@Data
@Slf4j
@Configuration
@ConfigurationProperties(prefix = "okhttp")
public class OkHttpClientConfig {

    @Bean
    public OkHttpClient okHttpClient(ProxyConfig proxyConfig) {
        try {
            log.info("Current User: {}", System.getProperty("user.name"));
            System.getenv().forEach((k, v) -> {
                if (k.toLowerCase().contains("proxy")) {
                    log.info("{}={}", k, v);
                }
            });

            // ==================== 【新增部分：开始】 ====================
            // 1. 创建一个信任所有证书的 TrustManager
            final TrustManager[] trustAllCerts = new TrustManager[]{
                    new X509TrustManager() {
                        @Override
                        public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
                        }

                        @Override
                        public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
                        }

                        @Override
                        public X509Certificate[] getAcceptedIssuers() {
                            return new X509Certificate[]{};
                        }
                    }
            };

            // 2. 基于我们自定义的 TrustManager 创建 SSLContext
            final SSLContext sslContext = SSLContext.getInstance("SSL");
            sslContext.init(null, trustAllCerts, new java.security.SecureRandom());

            // 3. 从 SSLContext 创建 SSLSocketFactory
            final SSLSocketFactory sslSocketFactory = sslContext.getSocketFactory();

            OkHttpClient.Builder builder = new OkHttpClient.Builder()
                    .connectTimeout(5, TimeUnit.SECONDS)
                    .retryOnConnectionFailure(true)
                    .followRedirects(true)
                    .followSslRedirects(true)
                    .addInterceptor(new RetryInterceptor(3, 1000)) // 添加重试拦截器，重试3次，间隔1秒
                    // 你可以继续添加其他的配置，比如超时
                    .connectTimeout(10, TimeUnit.SECONDS)
                    .readTimeout(10, TimeUnit.SECONDS);

            // ==================== 【新增部分：应用配置】 ====================
            // 4. 将我们自定义的 SSLSocketFactory 和 HostnameVerifier 应用到 builder
            builder.sslSocketFactory(sslSocketFactory, (X509TrustManager) trustAllCerts[0]);
            builder.hostnameVerifier((hostname, session) -> true); // 信任所有 hostname
            log.warn("SSL证书验证已禁用！创建了一个信任所有证书的 OkHttpClient Bean。");
            // ==============================================================


            if (proxyConfig.getEnabled()) {
                Proxy.Type type = proxyConfig.getType() == ProxyConfig.ProxyType.HTTP
                        ? Proxy.Type.HTTP
                        : Proxy.Type.SOCKS;
                final Proxy proxy = new Proxy(
                        type,
                        new InetSocketAddress(proxyConfig.getHost(), proxyConfig.getPort())
                );

                log.info("proxy config : {}://{}:{} (enable={})",
                        proxyConfig.getType().name().toLowerCase(),
                        proxyConfig.getHost(),
                        proxyConfig.getPort(),
                        proxyConfig.getEnabled());

                final List<String> noProxyHosts = proxyConfig.getNoProxyHosts();

                builder.proxySelector(new ProxySelector() {
                    /**
                     * 这个方法决定对给定的URI使用哪个代理。
                     * @param uri 正在请求的统一资源标识符。
                     * @return 代理列表。通常我们只返回一个。
                     */
                    @Override
                    public List<Proxy> select(URI uri) {
                        // 获取请求的主机名
                        String requestHost = uri.getHost();
                        // 检查该主机名是否在“不使用代理”的列表中
                        boolean shouldProxy = noProxyHosts == null || noProxyHosts.stream().noneMatch(requestHost::endsWith
                        );
                        if (shouldProxy) {
                            // 如果主机名不在 noProxyHosts 列表中，使用我们配置的代理
                            log.info("Using proxy for: " + requestHost);
                            System.out.println();
                            // 返回包含我们代理的列表
                            return Collections.singletonList(proxy);
                        } else {
                            // 如果主机名在 noProxyHosts 列表中，选择直连
                            log.info("Direct connection (no proxy) for: " + requestHost);
                            // 返回包含 Proxy.NO_PROXY 的列表，表示直连
                            return Collections.singletonList(Proxy.NO_PROXY);
                        }
                    }

                    /**
                     * 当连接到代理服务器失败时，此方法会被调用。
                     * 您可以在这里记录日志或实现故障转移逻辑。
                     * @param uri 失败的URI
                     * @param sa 失败的代理服务器地址
                     * @param ioe 发生的IO异常
                     */
                    @Override
                    public void connectFailed(URI uri, SocketAddress sa, IOException ioe) {
                        // 例如，记录一条错误日志
                        System.err.println("Failed to connect to proxy " + sa + " for URI " + uri);
                        ioe.printStackTrace();
                    }
                });

            } else {
                log.info("Proxy disabled");
            }

            return builder.build();

        } catch (Exception e) {
            log.error("创建自定义 OkHttpClient 失败", e);
            throw new RuntimeException(e);
        }
    }
}