package com.github.winefoxbot.plugins.pixiv.model.dto.bookmark;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * @author FlanChan (badapple495@outlook.com)
 * @since 2026-01-04-20:07
 */
@Data
public class PixivUnmarkApiRequest  {
    @JsonProperty("bookmark_id")
    private String bookmarkId;
}