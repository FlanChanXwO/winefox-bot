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
public enum GroupAdminChangeType implements BaseEnum<String> {
    SET("set"),
    UNSET("unset");

    @EnumValue
    @JsonValue
    private final String value;


    GroupAdminChangeType(String value) {
        this.value = value;
    }

    public static GroupAdminChangeType fromValue(String value) {
        for (GroupAdminChangeType role : GroupAdminChangeType.values()) {
            if (role.getValue().equals(value)) {
                return role;
            }
        }
        throw new IllegalArgumentException("Unknown value: " + value);
    }
}
