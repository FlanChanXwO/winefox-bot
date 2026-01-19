package com.github.winefoxbot.core.service.schedule.handler;

import com.mikuac.shiro.core.Bot;

/**
 * 自动注入 Bot 的任务处理器接口
 * @author FlanChan
 */
public interface BotJobHandler<T> {
    /**
     * 具体的任务逻辑
     * @param bot 在线的 Bot 实例（系统已自动获取并校验）
     * @param targetId 目标ID（群号/QQ号）
     * @param parameter 额外参数（可能是 null）
     */
    void run(Bot bot, Long targetId, T parameter);
}
