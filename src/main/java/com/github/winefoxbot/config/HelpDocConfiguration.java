package com.github.winefoxbot.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.winefoxbot.WineFoxBotApp;
import com.github.winefoxbot.model.dto.helpdoc.HelpData;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.system.ApplicationHome;
import org.springframework.context.annotation.Configuration;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

@Configuration
@Slf4j
@RequiredArgsConstructor
public class HelpDocConfiguration {
    @Getter
    private HelpData helpData;
    private final ObjectMapper mapper;

    @PostConstruct
    public void init() {
        try {
            // 将文件名定义为常量，避免魔法字符串
            final String configFileName = "config/help-docs.json";

            // 1. 判断运行环境
            ApplicationHome home = new ApplicationHome(WineFoxBotApp.class);
            File source = home.getSource();
            boolean isRunningFromJar = (source != null && source.isFile());

            if (isRunningFromJar) {
                log.info("[Runtime Checker] Running from JAR file, enabling external configuration loading.");
                // 在JAR环境下的逻辑
                loadConfigForJar(configFileName);
            } else {
                log.info("[Runtime Checker] Running in an IDE environment, loading from classpath only.");
                // 在IDE环境下的逻辑
                loadConfigFromClasspath(configFileName);
            }

            log.info("HelpDocConfiguration initialized successfully.");

        } catch (Exception e) { // 捕获更宽泛的异常，确保启动不失败
            log.error("Failed to initialize HelpDocConfiguration:", e);
            log.warn("Using default empty HelpData due to initialization failure.");
            // 确保 helpData 实例不为 null
            if (this.helpData == null) {
                this.helpData = new HelpData();
            }
        }
    }

    /**
     * 为JAR包运行环境加载配置。
     * 优先加载外部文件，如果不存在则从内部释放并加载。
     * @param configFileName 配置文件名
     */
    private void loadConfigForJar(String configFileName) throws IOException {
        //确保config目录存在
        Path configDir = Path.of("config");
        if (!Files.exists(configDir)) {
            Files.createDirectories(configDir);
            log.info("Created 'config' directory at application root.");
        }
        Path externalConfigPath = Path.of(configFileName);

        // 检查外部文件是否存在且可读
        if (Files.exists(externalConfigPath) && !Files.isDirectory(externalConfigPath) && Files.isReadable(externalConfigPath)) {
            log.info("Loading '{}' from application root (external file).", configFileName);
            try (InputStream inputStream = Files.newInputStream(externalConfigPath)) {
                this.helpData = mapper.readValue(inputStream, HelpData.class);
            }
        } else {
            // 外部文件不存在或不可读，从classpath加载并尝试创建默认外部文件
            log.info("External '{}' not found. Loading from classpath and creating a default external file.", configFileName);
            loadAndCreateDefault(configFileName, externalConfigPath);
        }
    }

    /**
     * 仅从类路径加载配置（用于IDE环境）。
     * @param configFileName 配置文件名
     */
    private void loadConfigFromClasspath(String configFileName) throws IOException {
        log.info("Loading '{}' from classpath.", configFileName);
        // 路径前缀'/'确保从classpath的根目录查找，更可靠
        try (InputStream inputStream = getClass().getResourceAsStream("/" + configFileName)) {
            if (inputStream == null) {
                throw new IOException("Classpath resource not found: " + configFileName);
            }
            this.helpData = mapper.readValue(inputStream, HelpData.class);
        }
    }

    /**
     * 从类路径加载配置，并尝试在外部创建一份默认配置。
     * @param configFileName 配置文件名
     * @param targetPath 目标外部路径
     */
    private void loadAndCreateDefault(String configFileName, Path targetPath) throws IOException {
        // 1. 从 Classpath 加载配置到内存
        loadConfigFromClasspath(configFileName);

        // 2. 再次打开 Classpath 的资源流，将其复制到外部文件
        //    (必须重新打开流，因为上一步操作已将其消耗)
        if (!Files.exists(targetPath)) {
            try (InputStream defaultConfigStream = getClass().getResourceAsStream("/" + configFileName)) {
                if (defaultConfigStream == null) {
                    // 理论上不会发生，因为loadConfigFromClasspath已经成功了
                    log.error("Could not find classpath resource '{}' to create default file.", configFileName);
                    return;
                }
                Files.copy(defaultConfigStream, targetPath);
                log.info("Default '{}' has been created at the application root. You can customize it.", configFileName);
            } catch (IOException e) {
                // 如果创建失败（例如权限问题），只记录错误，不影响程序运行
                log.error("Failed to create default external config file '{}':", configFileName, e);
            }
        }
    }
}
