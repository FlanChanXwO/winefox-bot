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
    /**
     * 上下文消息数量
     */
    private Integer contextSize = 60;
    /**
     * 是否启用图片分析功能
     */
    private Boolean enableImageAnalysis = true;
    /**
     * 是否启用表情发送
     */
    private Boolean enableEmoji = false;
}
