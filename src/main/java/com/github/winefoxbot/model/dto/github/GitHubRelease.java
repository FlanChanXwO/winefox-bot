package com.github.winefoxbot.model.dto.github;

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

