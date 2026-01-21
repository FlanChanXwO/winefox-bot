package com.github.winefoxbot.core.model.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import com.github.winefoxbot.core.model.type.BaseEnum;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 推送目标类型枚举
 * 遵循 BaseEnum 接口规范
 */
@Getter
@RequiredArgsConstructor
public enum PushTargetType implements BaseEnum<String> {

    GROUP("group", "群组"),
    PRIVATE("private", "私聊");

    @EnumValue // 存入数据库的值 (group, private)
    @JsonValue // 前端序列化展示的值
    private final String value;

    // 描述字段仅用于代码注释或日志，不参与持久化
    private final String description;

    @JsonCreator
    public static PushTargetType fromValue(String value) {
        if (value == null) {
            return null;
        }
        for (PushTargetType type : PushTargetType.values()) {
            if (type.getValue().equalsIgnoreCase(value)) {
                return type;
            }
        }
        // 这里可以选择抛出异常，或者返回 null 并在上层处理，视业务严谨度而定
        throw new IllegalArgumentException("Unknown PushTargetType value: " + value);
    }
}
