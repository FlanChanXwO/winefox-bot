package com.github.winefoxbot.core.model.enums.common;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import com.github.winefoxbot.core.model.type.BaseEnum;
import lombok.Getter;

/**
 * @author FlanChan (badapple495@outlook.com)
 * @since 2025-12-26-16:06
 */
@Getter
public enum GroupMemberDecreaseType implements BaseEnum<String> {
    LEAVE("leave"),
    KICK("kick"),
    KICK_ME("kick_me");

    @EnumValue
    @JsonValue
    private final String value;


    GroupMemberDecreaseType(String value) {
        this.value = value;
    }

    public static GroupMemberDecreaseType fromValue(String value) {
        for (GroupMemberDecreaseType role : GroupMemberDecreaseType.values()) {
            if (role.getValue().equals(value)) {
                return role;
            }
        }
        throw new IllegalArgumentException("Unknown value: " + value);
    }
}
