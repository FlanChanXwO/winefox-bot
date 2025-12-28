package com.github.winefoxbot.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.Data;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/**
 * @author FlanChan (badapple495@outlook.com)
 * @since 2025-12-10-16:21
 */
@Data
@Configuration
public class SetuApiConfig {
    private List<Api> apis;

        @Data
    public static class Api {
        private Integer id;
        private String url;
        private String responseType; // "json" 或 "image"
        private String jsonPath; // 仅当 responseType 为 "json" 时使用
        private R18KV r18;
    }

    @Data
    public static class R18KV {
        private String key;
        private String trueValue;
        private String falseValue;
    }

    @PostConstruct
    private void init() {
        loadSetuJson();
    }

    private void loadSetuJson() {
        final ObjectMapper objectMapper = new ObjectMapper();
        try (InputStream inputStream = getClass().getClassLoader().getResourceAsStream("setu.json")) {
            if (inputStream != null) {
                SetuApiConfig config = objectMapper.readValue(inputStream, SetuApiConfig.class);
                this.apis = config.getApis();
            } else {
                throw new IOException("setu.json not found in resources directory");
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to load setu.json", e);
        }
    }
}