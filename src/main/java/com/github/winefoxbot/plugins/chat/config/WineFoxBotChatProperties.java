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
    private String avatarDir = "classpath:avatars";
    /**
     * 使用的角色形象
     */
    private String avatar = "winefox";


    private Integer contextSize = 20;
}
