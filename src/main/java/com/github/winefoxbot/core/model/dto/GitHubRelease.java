package com.github.winefoxbot.core.model.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * GitHub Release DTO
 * @author FlanChan
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@Data
public class GitHubRelease {

    private Long id;
    @JsonProperty("tag_name")
    private String tagName;
    private Asset[] assets;
    /**
     * Release Notes (Markdown 格式的更新日志)
     */
    private String body;

    /**
     * 发布时间 (ISO 8601 格式，例如: 2025-01-14T12:00:00Z)
     */
    @JsonProperty("published_at")
    private String publishedAt;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Asset {

        private Long id; // 新增的字段，用于存储 Asset ID
        private String name;
        private String url;
        @JsonProperty("browser_download_url")
        private String browserDownloadUrl;
    }
}

