package com.github.winefoxbot.core.model.vo.webui.resp;

import lombok.Builder;

@Builder
public record PluginListItemResponse (
    String id,           // 插件唯一标识 (通常用 BeanName 或类名)
    String name,         // 显示名称
    String description,  // 描述
    String version,      // 版本
    String author,       // 作者
    String iconPath,     // 图标
    boolean enabled,     // 是否已开启
    boolean builtIn,     // 是否内置 (决定删除按钮是否禁用)
    boolean hasConfig  // 是否有配置项 (决定配置按钮是否显示)
) {}
