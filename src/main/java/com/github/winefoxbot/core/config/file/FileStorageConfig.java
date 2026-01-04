package com.github.winefoxbot.core.config.file;


import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.winefoxbot.core.service.file.FileStorageService;
import com.github.winefoxbot.core.service.file.impl.LocalStorageService;
import com.github.winefoxbot.core.service.file.impl.ObjectStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.nio.file.Path;
import java.nio.file.Paths;

@Configuration
@EnableScheduling // 启用定时任务，用于文件清理
@EnableConfigurationProperties(FileStorageProperties.class)
@RequiredArgsConstructor
public class FileStorageConfig {

    private final FileStorageProperties properties;
    private final ObjectMapper objectMapper;
    private final static String LOCAL_RECORD_FILENAME = ".records.json";

    @Bean
    public FileStorageService fileStorageService() {
        return switch (properties.getType().toLowerCase()) {
            case "local" -> {
                Path basePath = Paths.get(properties.getLocal().getBasePath());
                Path recordPath = basePath.resolve(LOCAL_RECORD_FILENAME);
                yield new LocalStorageService(basePath, objectMapper ,recordPath);
            }
            case "object" -> new ObjectStorageService();
            default -> throw new IllegalArgumentException("Unknown storage type: " + properties.getType());
        };
    }
}
