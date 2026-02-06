package com.github.winefoxbot.plugins.setu.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import com.github.winefoxbot.core.model.type.BaseEnum;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * @author FlanChan (badapple495@outlook.com)
 * @since 2026-02-05-18:54
 */
@Getter
@RequiredArgsConstructor
public enum ContentSendMode  implements BaseEnum<String> {
    FORWARD("forward", "合并转发"),
    IMAGE("image", "图片"),
    PDF("pdf", "PDF");

    @EnumValue
    @JsonValue
    private final String value;

    // 增加一个描述字段，用于前端下拉框的 Label 显示
    private final String description;
    @JsonCreator
    public static ContentSendMode fromValue(String value) {
        for (ContentSendMode mode : values()) {
            if (mode.value.equalsIgnoreCase(value)) {
                return mode;
            }
        }
        return IMAGE;
    }
}
