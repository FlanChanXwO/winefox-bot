package com.github.winefoxbot.plugins.chat.config;

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
@PluginConfig(prefix = "chat", scopes = {ConfigManager.Scope.GLOBAL, ConfigManager.Scope.GROUP})
public class ChatAiPluginConfig extends BasePluginConfig {
    private Boolean enableImageAnalysis;
}