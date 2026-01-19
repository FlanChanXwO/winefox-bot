package com.github.winefoxbot.core.annotation.plugin;

import java.lang.annotation.*;

/**
 * 标记字段为具体的配置项
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface ConfigItem {
    String key();           // 相对 Key，如 "enabled" (实际存储为 prefix.key)

    String description();   // 描述，用于 WebUI 显示

    String defaultValue();  // 默认值 (String 形式)
}
