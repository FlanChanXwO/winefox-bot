package com.github.winefoxbot.core.config.plugin;

import com.github.winefoxbot.core.annotation.plugin.ConfigItem;
import lombok.Data;

/**
 * 插件配置的基类 (规范)
 * 所有暴露给 WebUI 的插件配置类必须继承此方类。
 * 强制包含 enabled 字段，用于 WebUI 的总开关控制。
 * @author FlanChan
 */
@Data
public abstract class BasePluginConfig {

    /**
     * 插件总开关
     * key 固定为 "enabled"
     * 默认值为 "true" (开启)，因为通常安装了插件就是想用
     */
    @ConfigItem(
            key = "enabled", 
            description = "插件总开关", 
            defaultValue = "true"
    )
    private Boolean enabled;

    /**
     * 判断插件是否开启的便捷方法
     * 防止空指针异常，默认视为空配置为关闭，或者你可以根据业务改为默认开启
     */
    public boolean isEnabled() {
        return Boolean.TRUE.equals(enabled);
    }

    public static final class None extends BasePluginConfig {}
}
