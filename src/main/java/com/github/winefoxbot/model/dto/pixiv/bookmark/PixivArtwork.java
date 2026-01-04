package com.github.winefoxbot.model.dto.pixiv.bookmark;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class PixivArtwork {
    private String id;
    private String title;
    private Integer illustType;
    @JsonProperty("xRestrict")
    private Integer xRestrict;
    @JsonProperty("restrict")
    private Integer restrict;
    @JsonProperty("sl")
    private Integer sl;
    @JsonProperty("url")
    private String imageUrl;
    @JsonProperty("userId")
    private String authorId;
    @JsonProperty("userName")
    private String authorName;
    private Boolean isMasked;
    private Integer aiType;
    private Integer width;
    private Integer height;
    private Integer pageCount;
    private List<String> tags;
    private String description;
    private OffsetDateTime createDate;
    private OffsetDateTime updateDate;
    private BookMarkData bookmarkData;
    @Data
    public static class BookMarkData {
        private String id;
        @JsonProperty("private")
        private Boolean privated;
    }
}