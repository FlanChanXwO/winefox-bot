package com.github.winefoxbot.core.config.app;

import com.github.winefoxbot.core.annotation.webui.EnableConfigDashboard;
import com.github.winefoxbot.core.annotation.webui.ShowInDashboard;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * @author FlanChan (badapple495@outlook.com)
 * @since 2026-01-04-21:12
 */
@Data
@ConfigurationProperties(prefix = "winefox.robot")
@EnableConfigDashboard
public class WineFoxBotRobotProperties {
    /**
     * 机器人主人QQ号
     */
    @ShowInDashboard(label = "机器人主人QQ号", description = "拥有最高权限的用户QQ号")
    private List<Long> superUsers = List.of(3085974224L);
    /**
     * 机器人昵称
     */
    @ShowInDashboard(label = "机器人昵称", description = "机器人的显示名称")
    private String nickname = "酒狐";
    /**
     * 机器人主人名称
     */
    @ShowInDashboard(label = "机器人主人名称", description = "机器人的主人的显示名称")
    private String masterName = "雾理魔雨莎";
    /**
     * 机器人ID
     */
    @ShowInDashboard(label = "机器人ID", description = "机器人的唯一标识ID")
    private String botId = "114514";
}