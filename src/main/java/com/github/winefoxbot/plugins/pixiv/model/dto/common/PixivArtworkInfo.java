package com.github.winefoxbot.plugins.pixiv.model.dto.common;

import com.github.winefoxbot.plugins.pixiv.model.enums.PixivArtworkType;

import java.util.Collections;
import java.util.List;

/**
 * @author FlanChan (badapple495@outlook.com)
 * @since 2025-12-16-21:12
 */
public record PixivArtworkInfo(
        String pid,
        String title,
        String uid,
        String userName,
        String description,
        Boolean isR18,
        PixivArtworkType type,
        List<String> tags
) {
    // 紧凑构造函数，用于处理默认值防御性拷贝
    public PixivArtworkInfo {
        if (tags == null) {
            tags = Collections.emptyList();
        } else {
            // 保持不可变性
            tags = List.copyOf(tags);
        }
    }
}
