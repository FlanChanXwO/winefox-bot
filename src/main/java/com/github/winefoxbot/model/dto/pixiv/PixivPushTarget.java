package com.github.winefoxbot.model.dto.pixiv;

import lombok.Data;

/**
 * @author FlanChan (badapple495@outlook.com)
 * @since 2025-12-19-8:02
 */
@Data
public class PixivPushTarget {
    private long targetId;
    private String targetType; // "group" or "private"
}