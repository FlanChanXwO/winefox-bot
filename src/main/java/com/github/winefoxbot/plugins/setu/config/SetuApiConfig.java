package com.github.winefoxbot.plugins.setu.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

@Data
@Configuration
@ConfigurationProperties(prefix = "winefoxbot.plugins.setu.api")
// @EnableConfigurationProperties(...)  <-- 移除这一行
@Slf4j
@Validated
public class SetuApiConfig {

    @NotBlank(message = "API URL (setu.api.url) 不能为空")
    private String url;

    @NotNull(message = "API 响应类型 (setu.api.response-type) 不能为空")
    private ResponseType responseType;
    /**
     * JSON路径，用于从API响应中提取图片URL列表
     */
    private String jsonPath;

    private Params params = new Params();

    private Response response = new Response();

    public enum ResponseType {
        JSON,
        /**
         * 使用IMAGE模式，可能会导致无法正常处理R18模式
         */
        IMAGE
    }

    /**
     * API 查询参数的配置。
     */
    @Data
    public static class Params {
        private SimpleParamConfig num;
        private SimpleParamConfig excludeAI;
        private SimpleParamConfig tag;
        private ContentMode mode;
    }

    @Data
    public static class Response extends ParamConfig {
        private R18 r18;
    }

    @Data
    public static class R18 extends ParamConfig {
        private String jsonPath;
    }

    /**
     * 通用参数配置模型 (抽象基类)。
     */
    @Data
    public abstract static class ParamConfig {
        private String key;
        private String value;
    }

    /**
     * 新增：用于 num, excludeAI, tag 的具体参数配置类
     */
    @Data
    public static class SimpleParamConfig extends ParamConfig {}

    /**
     * 专门为 R18 配置的类。
     */
    @Data
    // @ConfigurationProperties(prefix = "setu.api.params.mode") <-- 移除这一行
    public static class ContentMode extends ParamConfig {
        private String safeModeValue;
        private String r18ModeValue;
        private String mixModeValue;
    }
}
