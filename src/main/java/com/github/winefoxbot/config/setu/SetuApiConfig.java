package com.github.winefoxbot.config.setu;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

/**
 * @author FlanChan (badapple495@outlook.com)
 * @since 2025-12-10-16:21 (Modified)
 * <p>
 * Setu API 配置类，从 application.yaml 加载单个可替换的 API 配置。
 * 包含了动态参数配置，以适应不同 API 的要求。
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "setu.api")
@Slf4j
@Validated
public class SetuApiConfig {

    /**
     * API 的基础请求地址 (不含查询参数)。
     * 例如: "https://api.lolicon.app/setu/v2"
     */
    @NotBlank(message = "API URL (setu.api.url) 不能为空")
    private String url;

    /**
     * 期望的响应类型。
     * 可选值: "json", "image"
     */
    @NotNull(message = "API 响应类型 (setu.api.response-type) 不能为空")
    private ResponseType responseType;

    /**
     * JSON 路径表达式。
     * 仅当 responseType 为 "json" 时使用，用于从 JSON 响应中提取图片 URL。
     */
    private String jsonPath;

    /**
     * API 查询参数的配置。
     * 可以在 YAML 中灵活定义参数的 key 和 value。
     */
    private Params params = new Params(); // 初始化以避免空指针

    public enum ResponseType {
        JSON,
        IMAGE
    }


    /**
     * 内部类，用于组织和映射所有动态参数。
     */
    @Data
    public static class Params {
        /**
         * 获取图片数量的参数。
         */
        private ParamConfig num;

        /**
         * 排除 AI 生成作品的参数。
         * 如果 API 支持，可以配置此项。
         */
        private ParamConfig excludeAI;

        /**
         * 按标签搜索的参数。
         */
        private ParamConfig tag;

        /**
         * R18 (成人内容) 切换的参数。
         */
        private R18Config r18;
    }

    /**
     * 通用参数配置模型。
     */
    @Data
    public static class ParamConfig {
        /**
         * 此参数在 URL 中的查询名称 (key)。
         * 例如: "num", "tag", "excludeAI"
         */
        private String key;

        /**
         * 此参数的默认值 (可选)。
         * 对于开关型参数，这通常是 "true" 或 "1"。
         */
        private String value;
    }

    /**
     * 专门为 R18 配置的类，因为它有 true/false 两种状态值。
     */
    @Data
    public static class R18Config extends ParamConfig {
        /**
         * R18 开启时的值。
         * 例如: "1"
         */
        private String trueValue;

        /**
         * R18 关闭时的值。
         * 例如: "0"
         */
        private String falseValue;
    }
}
