package com.github.winefoxbot.core.config.app;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * @author FlanChan (badapple495@outlook.com)
 * @since 2026-01-04-21:12
 */
@Data
@ConfigurationProperties(prefix = "winefox.robot")
public class WineFoxBotRobotProperties {
    /**
     * 机器人主人QQ号
     */
    private List<Long> superUsers = List.of(3085974224L);
    /**
     * 机器人昵称
     */
    private String nickname = "酒狐";
}