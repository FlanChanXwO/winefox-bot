package com.github.winefoxbot.core.config.plugin;

import lombok.Data;

/**
 * 插件配置的基类 (规范)
 * 所有暴露给 WebUI 的插件配置类必须继承此方类。
 * 强制包含 enabled 字段，用于 WebUI 的总开关控制。
 * @author FlanChan
 */
@Data
public abstract class BasePluginConfig {

    public static final class None extends BasePluginConfig {}
}
