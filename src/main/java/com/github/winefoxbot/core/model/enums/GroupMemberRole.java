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
public enum GroupMemberRole implements BaseEnum<String> {
    OWNER("owner", "群主"),
    ADMIN("admin", "管理员"),
    MEMBER("member", "普通成员");

    @EnumValue
    private final String value;

    @JsonValue
    private final String description;

    GroupMemberRole(String value, String description) {
        this.value = value;
        this.description = description;
    }

    public static GroupMemberRole fromValue(String value) {
        for (GroupMemberRole role : GroupMemberRole.values()) {
            if (role.getValue().equals(value)) {
                return role;
            }
        }
        throw new IllegalArgumentException("Unknown value: " + value);
    }
}
