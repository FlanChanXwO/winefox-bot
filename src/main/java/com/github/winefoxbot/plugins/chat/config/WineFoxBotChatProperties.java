package com.github.winefoxbot.plugins.chat.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * WineFoxBot Chat 插件配置属性
 *
 * @author FlanChan
 */
@Data
@ConfigurationProperties(prefix = "winefox.ai.chat")
public class WineFoxBotChatProperties {

    public static final String AVATAR_FILE_NAME = "avatar.md";
    /**
     * 角色形象储存位置
     */
    private String avatarDir = "avatars";
    /**
     * 使用的角色形象
     */
    private String avatar = "winefox";
    /**
     * 上下文消息数量
     */
    private Integer contextSize = 60;
    /**
     * 上下文 Token 最大限制
     */
    private Integer maxContextTokens = 25000;
    /**
     * 是否启用图片分析功能
     */
    private Boolean enableImageAnalysis = true;
}
