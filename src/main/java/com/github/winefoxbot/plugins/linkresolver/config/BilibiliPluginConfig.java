package com.github.winefoxbot.plugins.linkresolver.config;

import com.github.winefoxbot.core.annotation.plugin.ConfigItem;
import com.github.winefoxbot.core.annotation.plugin.PluginConfig;
import com.github.winefoxbot.core.config.plugin.BasePluginConfig;
import com.github.winefoxbot.core.manager.ConfigManager;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * B站插件配置类
 * <p>
 * 适配 WineFoxBot Core 新配置架构
 * </p>
 */
@EqualsAndHashCode(callSuper = true)
@Data
@PluginConfig(prefix = "bilibili", scopes = ConfigManager.Scope.GLOBAL)
public class BilibiliPluginConfig extends BasePluginConfig {

    @ConfigItem(key = "cookie", label = "B站 Cookie", description = "用于获取高清视频或番剧信息的 SESSDATA，留空则使用游客身份", defaultValue = "")
    private String cookie;

    @ConfigItem(key = "skip_video_analysis", label = "跳过视频详情", description = "是否仅解析链接而不发送视频详情卡片", defaultValue = "false")
    private Boolean skipVideoAnalysis;

    @ConfigItem(key = "display_image", label = "显示封面图", description = "分析结果中是否包含封面图片", defaultValue = "true")
    private Boolean analysisDisplayImage;

    @ConfigItem(key = "send_video_resource", label = "发送视频文件", description = "解析到视频时，是否尝试下载并作为视频文件发送", defaultValue = "true")
    private Boolean analysisVideoSend;

    @ConfigItem(key = "duration_limit", label = "视频时长限制(秒)", description = "超过该时长的视频将不下载发送，0表示不限制", defaultValue = "600")
    private Long durationSecLimit;

    @ConfigItem(key = "tmp_path", label = "临时路径", description = "视频下载临时存放目录", defaultValue = "data/bili_temp")
    private String tmpPath;

    @ConfigItem(key = "image_size", label = "图片尺寸", description = "图片处理参数，如 @300w_300h", defaultValue = "")
    private String imagesSize;

    @ConfigItem(key = "cover_image_size", label = "封面尺寸", description = "封面图处理参数", defaultValue = "")
    private String coverImagesSize;

    @ConfigItem(key = "cache_seconds", label = "防重复解析时间(秒)", description = "解析过的链接在多少秒内不会再次解析", defaultValue = "60")
    private Long reanalysisTimeSeconds;

    @ConfigItem(key = "send_image", label = "发送图片卡片", description = "是否使用图片卡片方式发送解析内容，关闭则使用文本", defaultValue = "true")
    private Boolean sendImage;
}