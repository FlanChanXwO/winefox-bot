package com.github.winefoxbot.plugins.pixiv.config;

import com.github.winefoxbot.core.annotation.plugin.ConfigItem;
import com.github.winefoxbot.core.annotation.plugin.PluginConfig;
import com.github.winefoxbot.core.config.plugin.BasePluginConfig;
import com.github.winefoxbot.core.manager.ConfigManager;
import com.github.winefoxbot.plugins.pixiv.model.enums.AdultContentMode;
import com.github.winefoxbot.plugins.pixiv.model.enums.ContentSendMode;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * @author FlanChan
 */
@EqualsAndHashCode(callSuper = true)
@Data
@PluginConfig(prefix = "pixiv" ,scopes = {ConfigManager.Scope.GLOBAL, ConfigManager.Scope.GROUP, ConfigManager.Scope.USER})
public class PixivPluginConfig extends BasePluginConfig {

    @ConfigItem(
            key = "content.mode",
            label = "p站内容模式",
            description = "p站容模式。可选值：sfw (安全), r18 (仅成人), mix (混合)",
            defaultValue = "sfw"
    )
    private AdultContentMode contentMode;

    @ConfigItem(
            key = "revoke.enabled", // 对应 setu.revoke.enabled
            label = "自动撤回 pdf",
            description = "是否开启 pdf 内容自动撤回",
            defaultValue = "true"
    )
    private boolean revokeEnabled;


    @ConfigItem(
            key = "revoke.delay",   // 对应 setu.revoke.delay
            label = "pdf 撤回延迟时间",
            description = "pdf 内容自动撤回延迟时间 (秒)",
            defaultValue = "30"
    )
    private int revokeDelay;


    @ConfigItem(
            key = "send.mode",
            label = "发送方式",
            description = "仅限SFW类型使用",
            defaultValue = "image"
    )
    private ContentSendMode sendMode;


    @ConfigItem(
            key = "artwork.info",
            label = "作品信息",
            description = "是否发送作品信息",
            defaultValue = "false"
    )
    private boolean sendArtworkInfo;

}
