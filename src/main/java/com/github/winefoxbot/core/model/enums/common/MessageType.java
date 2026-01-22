package com.github.winefoxbot.core.model.enums.common;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import com.github.winefoxbot.core.model.type.BaseEnum;
import lombok.Getter;

/**
 * @author FlanChan (badapple495@outlook.com)
 * @since 2025-12-26-16:06
 */
@Getter
public enum MessageType implements BaseEnum<String> {
    GROUP("group"),
    PRIVATE("private");

    @EnumValue
    @JsonValue
    private final String value;

    MessageType(String value) {
        this.value = value;
    }

    @JsonCreator
    public static MessageType fromValue(String value) {
        for (MessageType role : MessageType.values()) {
            if (role.getValue().equalsIgnoreCase(value)) {
                return role;
            }
        }
        throw new IllegalArgumentException("Unknown value: " + value);
    }
}
