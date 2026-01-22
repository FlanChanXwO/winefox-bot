package com.github.winefoxbot.core.model.enums.common;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum ConnectionEventType {
    CONNECT("CONNECT", "连接"),
    DISCONNECT("DISCONNECT", "断开");

    @EnumValue // 存入数据库的值
    private final String code;
    
    @JsonValue // 返回给前端展示的值（或者你也可以返回 desc）
    private final String desc;
}
