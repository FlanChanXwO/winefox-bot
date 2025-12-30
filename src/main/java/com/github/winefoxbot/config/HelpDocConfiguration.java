package com.github.winefoxbot.config;

import com.fasterxml.jackson.databind.ObjectMapper;
// [修改] 导入新的DTO
import com.github.winefoxbot.model.dto.helpdoc.HelpData;
import jakarta.annotation.PostConstruct;
import lombok.Getter; // [新增] 导入 Getter
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;

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
            // [推荐] 将文件名定义为常量，避免魔法字符串
            String configFileName = "help-docs.json";
            Path configPath = Path.of(configFileName);
            boolean exists = Files.exists(configPath);

            if (exists && !Files.isDirectory(configPath) && Files.isReadable(configPath)) {
                log.info("Loading {} from application root path.", configFileName);
                try (InputStream inputStream = Files.newInputStream(configPath)) {
                    this.helpData = mapper.readValue(inputStream, HelpData.class);
                }
            } else {
                log.info("Loading {} from classpath.", configFileName);
                // [修改] 路径前缀'/'更可靠
                try (InputStream inputStream = getClass().getResourceAsStream("/" + configFileName)) {
                    if (inputStream == null) {
                        throw new IOException("Classpath resource not found: " + configFileName);
                    }
                    this.helpData = mapper.readValue(inputStream, HelpData.class);

                    if (!exists) {
                        try (InputStream defaultConfigStream = getClass().getResourceAsStream("/" + configFileName)) {
                            Files.copy(defaultConfigStream, configPath);
                            log.info("Default {} created at application root path.", configFileName);
                        } catch (IOException e) {
                            log.error("Failed to create default {}:", configFileName, e);
                        }
                    }
                }
            }
            log.info("HelpDocConfiguration initialized successfully.");
        } catch (IOException e) {
            log.error("Failed to initialize HelpDocConfiguration:", e);
            log.warn("Using default empty HelpData due to initialization failure.");
            this.helpData = new HelpData();
        }
    }
}
