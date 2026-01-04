package com.github.winefoxbot.core.model.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import com.github.winefoxbot.core.model.type.BaseEnum;
import lombok.Getter;

/**
 * @author FlanChan (badapple495@outlook.com)
 * @since 2025-12-26-16:06
 */
@Getter
public enum SessionType implements BaseEnum<String> {
    GROUP("group"),
    PRIVATE("private");

    @EnumValue
    @JsonValue
    private final String value;


    SessionType(String value) {
        this.value = value;
    }

    public static SessionType fromValue(String value) {
        for (SessionType role : SessionType.values()) {
            if (role.getValue().equals(value)) {
                return role;
            }
        }
        throw new IllegalArgumentException("Unknown value: " + value);
    }
}
