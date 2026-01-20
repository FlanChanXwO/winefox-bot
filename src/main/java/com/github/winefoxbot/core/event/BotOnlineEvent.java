package com.github.winefoxbot.core.event;

import com.mikuac.shiro.core.Bot;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

/**
 * Bot 上线事件，用于触发耗时的后台任务（如刷新群组/好友列表）
 */
@Getter
public class BotOnlineEvent extends ApplicationEvent {
    private final Bot bot;

    public BotOnlineEvent(Object source, Bot bot) {
        super(source);
        this.bot = bot;
    }
}
