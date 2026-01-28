package com.github.winefoxbot.plugins.chat.model.dto;

import java.util.List;

/**
 * 用于描述表情包信息的DTO。
 *
 * @param id
 * @param path
 * @param keywords
 * @param description
 * @param intent
 * @param emotion
 */
public record EmoteData(
        Integer id,
        String path,
        List<String> keywords,
        String description,
        String intent,
        String emotion
) {
}
