package com.github.winefoxbot.core.model.vo.webui.req.config;


public record UpdateConfigRequest (
    String scope,   // GLOBAL, GROUP, USER
    String scopeId, // default, 群号, QQ号
    String key,     // 配置键 (如 setu.content.mode)
    Object value,   // 值
    String description, // 可选
    String group       // 可选，配置组名
) {}
