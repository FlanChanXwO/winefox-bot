package com.github.winefoxbot.plugins.chat.config;

import com.github.winefoxbot.core.annotation.plugin.ConfigItem;
import com.github.winefoxbot.core.annotation.plugin.PluginConfig;
import com.github.winefoxbot.core.config.plugin.BasePluginConfig;
import com.github.winefoxbot.core.manager.ConfigManager;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * @author FlanChan (badapple495@outlook.com)
 * @since 2026-02-12-20:14
 */
@EqualsAndHashCode(callSuper = true)
@Data
@PluginConfig(prefix = "chat", scopes = ConfigManager.Scope.GLOBAL)
public class ChatAiPluginConfig extends BasePluginConfig {
    @ConfigItem(key = "image-analysis",label = "图片识别", description = "是否启用图片识别", defaultValue = "false")
    private Boolean enableImageAnalysis;
}