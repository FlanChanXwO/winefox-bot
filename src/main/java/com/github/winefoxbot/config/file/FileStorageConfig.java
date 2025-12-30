package com.github.winefoxbot.config.file;


import com.github.winefoxbot.service.file.FileStorageService;
import com.github.winefoxbot.service.file.impl.LocalStorageService;
import com.github.winefoxbot.service.file.impl.ObjectStorageService;
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

    @Bean
    public FileStorageService fileStorageService() {
        return switch (properties.getType().toLowerCase()) {
            case "local" -> {
                Path basePath = Paths.get(properties.getLocal().getBasePath());
                Path recordPath = basePath.resolve(".records.json");
                yield new LocalStorageService(basePath, recordPath);
            }
            case "object" -> new ObjectStorageService();
            default -> throw new IllegalArgumentException("Unknown storage type: " + properties.getType());
        };
    }
}
