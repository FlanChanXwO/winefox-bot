package com.github.winefoxbot.core.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

/**
 * Bot 上线事件，用于触发耗时的后台任务（如刷新群组/好友列表）
 */
@Getter
public class BotOfflineEvent extends ApplicationEvent {
    private final long account ;

    public BotOfflineEvent(Object source, long account) {
        super(source);
        this.account = account;
    }
}
