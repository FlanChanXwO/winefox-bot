package com.github.winefoxbot.core.service.schedule.handler;

import com.github.winefoxbot.core.config.plugin.BasePluginConfig;
import com.github.winefoxbot.core.model.enums.PushTargetType;
import com.mikuac.shiro.core.Bot;

/**
 * 自动注入 Bot 的任务处理器接口
 * @author FlanChan
 */
public interface BotJobHandler<P, C extends BasePluginConfig> {
    /**
     * 具体的任务逻辑
     * @param bot 在线的 Bot 实例（系统已自动获取并校验）
     * @param targetId 目标ID（群号/QQ号）
     * @param parameter 额外参数（可能是 null）
     */
    void run(Bot bot, Long targetId, PushTargetType targetType, P parameter);

    /**
     * 默认返回 None 配置，如果具体的 Job 需要配置，重写此方法返回具体的 Config Bean
     */
    @SuppressWarnings("unchecked")
    default C getPluginConfig() {
        return (C) new BasePluginConfig.None();
    }
}
