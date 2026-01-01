package com.github.winefoxbot.model.dto.pixiv;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class PixivApiResult {
    private boolean error;
    private Body body;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Body {
        @JsonProperty("illustManga")
        private IllustManga illustManga;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class IllustManga {
        private List<Artwork> data;
        private long total;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Artwork {
        private String id;
        private String title;
        private String url; // The thumbnail URL we need
        @JsonProperty("userId")
        private String userId;
        @JsonProperty("userName")
        private String userName;
        @JsonProperty("profileImageUrl")
        private String profileImageUrl;
        private int width;
        private int height;
        @JsonProperty("pageCount")
        private int pageCount;
        @JsonProperty("aiType")
        private int aiType; // 2 means it's an AI-generated work
    }
}
