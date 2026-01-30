package com.github.winefoxbot.core.config.inner;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.server.Ssl;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.boot.web.server.autoconfigure.ServerProperties;
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
@RequiredArgsConstructor
public class AutoSslConfig implements WebServerFactoryCustomizer<ConfigurableServletWebServerFactory> {

    private final ServerProperties serverProperties;

    private static final String CERT_DIR = System.getProperty("cert.dir", "certs");


    @Override
    public void customize(ConfigurableServletWebServerFactory factory) {
        Path certRoot = Paths.get(CERT_DIR);
        log.info("🔍 正在目录中扫描证书: " + certRoot.toAbsolutePath());
        if (!certRoot.toFile().exists()) {
            log.warn("⚠️ 证书目录不存在，跳过 SSL 配置。");
            return;
        }

        try (Stream<Path> stream = Files.walk(certRoot, 1)) {
            // 1. 寻找 .pfx 文件
            Optional<Path> pfxFileOpt = stream
                    .filter(p -> p.toString().toLowerCase().endsWith(".pfx"))
                    .findFirst();

            if (pfxFileOpt.isPresent()) {
                Path pfxPath = pfxFileOpt.get();
                log.info("✅ 找到证书文件: {}", pfxPath.getFileName());

                // 2. 寻找对应的密码文件 (假设密码文件就在旁边，且以 .txt 结尾)
                // 逻辑：在该目录下找任意一个包含 "password" 字样的 txt 文件，或者直接找那个特定文件
                String password = findPassword(certRoot);

                if (password != null) {
                    configureSsl(factory, pfxPath, password);
                } else {
                    log.warn("⚠️ 找到了 .pfx 证书，但未找到包含密码的 .txt 文件，SSL 无法开启。");
                }
            } else {
                log.warn("ℹ️ 未检测到 .pfx 证书文件，跳过 SSL 配置。");
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private String findPassword(Path dir) throws IOException {
        try (Stream<Path> stream = Files.walk(dir, 1)) {
            Optional<Path> passFile = stream
                    .filter(p -> {
                        String name = p.getFileName().toString().toLowerCase();
                        // 匹配规则：是txt文件，且文件名包含 password
                        return name.endsWith(".txt") && name.contains("password");
                    })
                    .findFirst();

            if (passFile.isPresent()) {
                log.info("✅ 找到密码文件: {}", passFile.get().getFileName());
                // 读取文件内容并去除首尾空格
                return Files.readString(passFile.get()).trim();
            }
        }
        return null;
    }

    private void configureSsl(ConfigurableServletWebServerFactory factory, Path pfxPath, String password) {
        Ssl ssl = new Ssl();
        ssl.setEnabled(true);
        // 设置证书路径
        ssl.setKeyStore(pfxPath.toAbsolutePath().toString());
        // 设置读取到的密码
        ssl.setKeyStorePassword(password);
        // pfx 就是 PKCS12
        ssl.setKeyStoreType("PKCS12");
        factory.setSsl(ssl);
        log.info("🚀 SSL 配置成功！");
    }
}
