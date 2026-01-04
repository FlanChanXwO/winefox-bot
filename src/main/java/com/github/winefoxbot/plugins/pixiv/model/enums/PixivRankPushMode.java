package com.github.winefoxbot.plugins.pixiv.model.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import com.github.winefoxbot.core.model.type.BaseEnum;
import lombok.Getter;

/**
 * @author FlanChan (badapple495@outlook.com)
 * @since 2025-12-26-16:06
 */
@Getter
public enum PixivRankPushMode implements BaseEnum<String> {
    DALLY("daily"),
    WEEKLY("weekly"),
    MONTHLY("monthly"),
    ;

    @EnumValue
    @JsonValue
    private final String value;


    PixivRankPushMode(String value) {
        this.value = value;
    }

    public static PixivRankPushMode fromValue(String value) {
        for (PixivRankPushMode role : PixivRankPushMode.values()) {
            if (role.getValue().equals(value)) {
                return role;
            }
        }
        throw new IllegalArgumentException("Unknown value: " + value);
    }
}
