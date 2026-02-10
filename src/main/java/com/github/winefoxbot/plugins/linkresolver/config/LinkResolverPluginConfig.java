package com.github.winefoxbot.plugins.linkresolver.config;

import com.github.winefoxbot.core.annotation.plugin.ConfigItem;
import com.github.winefoxbot.core.annotation.plugin.PluginConfig;
import com.github.winefoxbot.core.config.plugin.BasePluginConfig;
import com.github.winefoxbot.core.manager.ConfigManager;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 插件配置类
 */
@EqualsAndHashCode(callSuper = true)
@Data
@PluginConfig(prefix = "linkresolver", scopes = {ConfigManager.Scope.GLOBAL,ConfigManager.Scope.GROUP})
public class LinkResolverPluginConfig extends BasePluginConfig {

    @ConfigItem(key = "bilibili-cookie", label = "B站 Cookie", description = "用于获取高清视频或番剧信息的 SESSDATA，留空则使用游客身份", defaultValue = "")
    private String biliBilicookie;

    @ConfigItem(key = "send-card", label = "发送卡片", description = "是否发送解析卡片", defaultValue = "true")
    private Boolean sendCard;

    @ConfigItem(key = "send-resource", label = "发送资源", description = "是否发送与解析相关的媒体，如视频或图片", defaultValue = "true")
    private Boolean sendResource;

    @ConfigItem(key = "duration-limit", label = "视频时长限制(秒)", description = "超过该时长的视频将不下载发送，0表示不限制", defaultValue = "600")
    private Long durationSecLimit;
}