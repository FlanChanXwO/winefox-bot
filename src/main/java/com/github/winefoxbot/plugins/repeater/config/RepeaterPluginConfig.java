package com.github.winefoxbot.plugins.repeater.config;

import com.github.winefoxbot.core.annotation.plugin.ConfigItem;
import com.github.winefoxbot.core.annotation.plugin.PluginConfig;
import com.github.winefoxbot.core.config.plugin.BasePluginConfig;
import com.github.winefoxbot.core.manager.ConfigManager;
import com.github.winefoxbot.plugins.setu.enums.AdultContentMode;
import com.github.winefoxbot.plugins.setu.enums.ContentSendMode;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

/**
 * @author FlanChan
 */
@EqualsAndHashCode(callSuper = true)
@Data
@PluginConfig(prefix = "repeater" ,scopes = {ConfigManager.Scope.GLOBAL, ConfigManager.Scope.GROUP})
public class RepeaterPluginConfig extends BasePluginConfig {

    @ConfigItem(
            key = "maxFollowersPerGroup",   // 对应 setu.revoke.delay
            label = "复读跟随名额",
            description = "最大复读跟随名额，默认为10个",
            defaultValue = "10"
    )
    private int maxFollowersPerGroup;

    @ConfigItem(
            key = "shortestTimes",
            label = "最少复读次数",
            description = "触发复读的最少重复次数，默认为4",
            defaultValue = "4"
    )
    private int shortestTimes;

    @ConfigItem(
            key = "blacklistMessages",
            label = "复读黑名单消息",
            description = "以逗号分隔的黑名单消息，匹配时不会触发复读",
            defaultValue = "[]"
    )
    private List<String> blacklist;
}
