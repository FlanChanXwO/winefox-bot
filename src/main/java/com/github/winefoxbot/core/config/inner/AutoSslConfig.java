package com.github.winefoxbot.core.config.inner;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.server.Ssl;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.boot.web.server.servlet.ConfigurableServletWebServerFactory;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * 自动SSL检测配置器
 */
@Configuration
@Slf4j
public class AutoSslConfig implements WebServerFactoryCustomizer<ConfigurableServletWebServerFactory> {

    private static final String CERT_DIR = System.getProperty("cert.dir", "certs");

    private static final String KEYSTORE_PASSWORD = System.getenv().getOrDefault("SSL_PWD", "changeit"); // 建议通过环境变量获取密码

    @Override
    public void customize(ConfigurableServletWebServerFactory factory) {
        Path certPath = Paths.get(CERT_DIR);
        
        if (!Files.exists(certPath) || !Files.isDirectory(certPath)) {
            log.warn("⚠️ 证书目录 {} 不存在或不是目录，跳过 SSL 配置。", certPath.toAbsolutePath());
            return;
        }

        try (Stream<Path> stream = Files.walk(certPath, 1)) {
            Optional<Path> keystore = stream
                    .filter(p -> {
                        String name = p.getFileName().toString();
                        return name.endsWith(".p12") || name.endsWith(".jks") || name.endsWith(".keystore");
                    })
                    .findFirst();

            if (keystore.isPresent()) {
                configureSsl(factory, keystore.get());
            } else {
                System.out.println("⚠️ 证书目录为空，SSL未启用。");
            }

        } catch (IOException e) {
            throw new RuntimeException("读取证书目录失败", e);
        }
    }

    private void configureSsl(ConfigurableServletWebServerFactory factory, Path keystorePath) {
        log.info("✅ 启用 SSL，使用证书文件: {}", keystorePath.toAbsolutePath());
        Ssl ssl = new Ssl();
        ssl.setEnabled(true);
        ssl.setKeyStore(keystorePath.toAbsolutePath().toString());
        ssl.setKeyStorePassword(KEYSTORE_PASSWORD);
        if (keystorePath.toString().endsWith(".jks")) {
            ssl.setKeyStoreType("JKS");
        } else {
            ssl.setKeyStoreType("PKCS12");
        }

        factory.setSsl(ssl);
    }
}
