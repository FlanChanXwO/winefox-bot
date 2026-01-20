package com.github.winefoxbot.core.model.enums;

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
public enum MessageDirection implements BaseEnum<String> {
    MESSAGE_SENT("message_sent"),
    MESSAGE_RECEIVE("message");

    @EnumValue
    @JsonValue
    private final String value;

    MessageDirection(String value) {
        this.value = value;
    }
    @JsonCreator
    public static MessageDirection fromValue(String value) {
        for (MessageDirection role : MessageDirection.values()) {
            if (role.getValue().equalsIgnoreCase(value)) {
                return role;
            }
        }
        throw new IllegalArgumentException("Unknown value: " + value);
    }
}
