package com.github.winefoxbot.core.model.enums.common;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum PluginType {
    /**
     * 主动插件 (Active)
     * 通常通过命令触发 (如 /setu, /help)
     * 默认类型
     */
    ACTIVE("主动功能"),

    /**
     * 被动插件 (Passive)
     * 通常通过事件触发 (如 进群欢迎、消息监听、定时任务)
     * 这种插件通常需要在 WebUI 的侧边栏或特定区域展示状态
     */
    PASSIVE("被动响应");

    private final String description;
}