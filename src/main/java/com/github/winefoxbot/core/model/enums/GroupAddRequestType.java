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
public enum GroupAddRequestType implements BaseEnum<String> {
    /**
     * 新成员加群
     */
    ADD("add"),
    /**
     * BOT被邀请入群
     */
    INVITE("invite");

    @EnumValue
    @JsonValue
    private final String value;


    GroupAddRequestType(String value) {
        this.value = value;
    }

    public static GroupAddRequestType fromValue(String value) {
        for (GroupAddRequestType role : GroupAddRequestType.values()) {
            if (role.getValue().equals(value)) {
                return role;
            }
        }
        throw new IllegalArgumentException("Unknown value: " + value);
    }
}
