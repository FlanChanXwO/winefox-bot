package com.github.winefoxbot.core.model.enums.common;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import com.github.winefoxbot.core.model.type.BaseEnum;
import lombok.Getter;

/**
 * @author FlanChan (badapple495@outlook.com)
 * @since 2026-01-19-16:09
 */
@Getter
public enum RequestStatus implements BaseEnum<String> {
    PENDING("PENDING", "待处理"),
    ACCEPTED("ACCEPTED", "已接受"),
    REJECTED("REJECTED", "已拒绝"),
    IGNORED("IGNORED", "已忽略");

    @EnumValue
    private final String value;
    @JsonValue
    private final String description;

    RequestStatus(String value, String description) {
        this.value = value;
        this.description = description;
    }
}
