package com.github.winefoxbot.core.plugins.adultmanage.config;

import com.github.winefoxbot.core.annotation.plugin.ConfigItem;
import com.github.winefoxbot.core.annotation.plugin.PluginConfig;
import com.github.winefoxbot.core.config.plugin.BasePluginConfig;
import com.github.winefoxbot.core.model.enums.AdultContentMode;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 成人内容插件的配置定义
 * 前缀对应 ConfigConstants.AdultContent 中的 key 结构
 * 假设原 key 为:
 * setu.content.mode
 * setu.revoke.enabled (注意原代码里 key 是 ADULT_AUTO_REVOKE_ENABLED，需要确认前缀)
 * setu.revoke.delay
 * @author FlanChan
 */
@Data
@EqualsAndHashCode(callSuper = true)
@PluginConfig(prefix = "setu", name = "成人内容配置") 
public class AdultContentConfig extends BasePluginConfig {

    @ConfigItem(
            key = "content.mode",
            description = "福利内容模式。可选值：sfw (安全), r18 (仅成人), mix (混合)",
            defaultValue = "sfw"
    )
    private AdultContentMode contentMode;

    @ConfigItem(
            key = "revoke.enabled", // 对应 setu.revoke.enabled
            description = "是否开启 R18 内容自动撤回",
            defaultValue = "true"
    )
    private boolean revokeEnabled;

    @ConfigItem(
            key = "revoke.delay",   // 对应 setu.revoke.delay
            description = "R18 内容自动撤回延迟时间 (秒)",
            defaultValue = "30"
    )
    private int revokeDelay;
}
