package com.github.winefoxbot.model.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import com.github.winefoxbot.model.type.BaseEnum;
import lombok.Getter;

/**
 * @author FlanChan (badapple495@outlook.com)
 * @since 2025-12-26-16:06
 */
@Getter
public enum PixivArtworkType implements BaseEnum<Integer> {
    IMAGE(0),
    MANGA(1),
    GIF(2);

    @EnumValue
    @JsonValue
    private final Integer value;

    PixivArtworkType(Integer value) {
        this.value = value;
    }

    public static PixivArtworkType fromValue(Integer value) {
        for (PixivArtworkType role : PixivArtworkType.values()) {
            if (role.getValue().equals(value)) {
                return role;
            }
        }
        throw new IllegalArgumentException("Unknown value: " + value);
    }
}
