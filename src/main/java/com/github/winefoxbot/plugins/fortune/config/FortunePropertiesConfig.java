package com.github.winefoxbot.plugins.fortune.config;

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
@PluginConfig(prefix = "fortune",name = "运势插件配置", scopes = ConfigManager.Scope.GLOBAL)
public class FortunePropertiesConfig extends BasePluginConfig {
    /**
     * 图片标签
     */
    @ConfigItem(key = "tag", description = "用于生成运势图片的标签，默认标签为'碧蓝档案'", defaultValue = "ブルーアーカイブ")
    private String tag;
    /**
     * 是否允许刷新运势
     */
    @ConfigItem(key = "allow_refresh", description = "是否允许用户刷新运势，默认不允许", defaultValue = "false")
    private Boolean allowRefresh;
}