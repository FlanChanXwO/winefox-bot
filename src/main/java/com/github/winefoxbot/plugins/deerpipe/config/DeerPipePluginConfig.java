package com.github.winefoxbot.plugins.deerpipe.config;

import com.github.winefoxbot.core.annotation.plugin.ConfigItem;
import com.github.winefoxbot.core.annotation.plugin.PluginConfig;
import com.github.winefoxbot.core.config.plugin.BasePluginConfig;
import com.github.winefoxbot.core.manager.ConfigManager;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * @author FlanChan (badapple495@outlook.com)
 * @since 2026-01-21-18:28
 */
@EqualsAndHashCode(callSuper = true)
@Data
@PluginConfig(prefix = "deerpipe", scopes = {ConfigManager.Scope.GLOBAL,ConfigManager.Scope.GROUP, ConfigManager.Scope.USER})
public class DeerPipePluginConfig extends BasePluginConfig {
    /**
     * 是否允许无限补签，默认不允许
     */
    @ConfigItem(key = "allow_replenish_nolimit", label = "无限补签", description = "是否允许无限补签，默认不允许", defaultValue = "false")
    private Boolean allowReplenishNoLimit;
    /**
     * 是否允许用户开启防🦌护盾，默认允许
     */
    @ConfigItem(key = "allow_shield", label = "防🦌护盾", description = "是否允许用户开启防🦌护盾，默认允许", defaultValue = "true")
    private Boolean allowShield;
}