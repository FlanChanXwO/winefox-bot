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
@PluginConfig(name = "水群统计配置", prefix = "water_group" ,scopes = ConfigManager.Scope.GLOBAL)
public class WaterGroupConfig extends BasePluginConfig {

    @ConfigItem(key = "limit", defaultValue = "10", description = "排名数量")
    private Integer limit;
}
