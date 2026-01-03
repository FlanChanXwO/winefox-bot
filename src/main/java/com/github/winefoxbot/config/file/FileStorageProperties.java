package com.github.winefoxbot.config.file;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "app.storage")
public class FileStorageProperties {
    /**
     * 存储类型，可选 'local' 或 'object'
     */
    private String type = "local";
    private final Local local = new Local();

    @Data
    public static class Local {
        /**
         * 本地存储的根目录
         */
        private String basePath = "./data/files";
    }
}
