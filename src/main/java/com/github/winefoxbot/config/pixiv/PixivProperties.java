package com.github.winefoxbot.config.pixiv;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "pixiv")
@Data
public class PixivProperties {
    private Bookmark bookmark;
    private CookieProperties cookie;
    private AuthorizationProperties authorization;
    private ApiProperties api;

    @Data
    @ConfigurationProperties(prefix = "pixiv.bookmark")
    public static class Bookmark {
        private Tracker tracker;
        private Boolean allowUnmarkExpiredArtworks = false;
    }

    @Data
    @ConfigurationProperties(prefix = "pixiv.bookmark.tracker")
    public static class Tracker {
        private Boolean enabled = false;
        private String lightCron = "0 5 * * * ?";
        private String fullCron = "0 0 3 * * ?";
        private String targetUserId;
    }

    @Data
    @ConfigurationProperties(prefix = "pixiv.authorization")
    public static class AuthorizationProperties {
        private String xcsrfToken;
    }

    @Data
    @ConfigurationProperties(prefix = "pixiv.cookie")
    public static class CookieProperties {
        private String phpsessid;
        private String pAbId;
    }

    @Data
    @ConfigurationProperties(prefix = "pixiv.api")
    public static class ApiProperties {
        private String bookmarkUrlTemplate = "https://www.pixiv.net/ajax/user/{userId}/illusts/bookmarks?tag={tag}&offset={offset}&limit={limit}&rest=show&lang=zh";
        private Integer limitPerPage = 48;
        private String unmaskUrlTemplate = "https://www.pixiv.net/ajax/illusts/bookmarks/delete";
    }
}