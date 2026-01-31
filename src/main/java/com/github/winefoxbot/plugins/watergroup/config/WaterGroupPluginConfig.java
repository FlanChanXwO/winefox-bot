package com.github.winefoxbot.plugins.watergroup.config;

import com.github.winefoxbot.core.annotation.plugin.ConfigItem;
import com.github.winefoxbot.core.annotation.plugin.PluginConfig;
import com.github.winefoxbot.core.config.plugin.BasePluginConfig;
import com.github.winefoxbot.core.manager.ConfigManager;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * @author FlanChan
 */
@EqualsAndHashCode(callSuper = true)
@Data
@PluginConfig( prefix = "water_group" ,scopes = ConfigManager.Scope.GLOBAL)
public class WaterGroupPluginConfig extends BasePluginConfig {

    @ConfigItem(key = "limit", label = "渲染排名范围", defaultValue = "10", description = "在卡片中显示前多少名")
    private Integer limit;
}
