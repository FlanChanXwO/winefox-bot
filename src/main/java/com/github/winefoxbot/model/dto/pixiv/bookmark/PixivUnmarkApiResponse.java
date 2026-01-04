package com.github.winefoxbot.model.dto.pixiv.bookmark;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

/**
 * @author FlanChan (badapple495@outlook.com)
 * @since 2026-01-04-20:07
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class PixivUnmarkApiResponse {
    private Boolean error;
    private String message;
}