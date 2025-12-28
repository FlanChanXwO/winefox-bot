package com.github.winefoxbot.exception;

import com.mikuac.shiro.core.Bot;
import com.mikuac.shiro.dto.event.message.MessageEvent;
import lombok.Getter;

/**
 * 自定义限流异常
 * <p>
 * 当请求速率超过注解 @Limit 定义的阈值时，由 LimitAspect 抛出。
 * 此异常包含触发限流时的上下文信息，如 Bot 实例、消息事件和提示消息，
 * 以便全局异常处理器能够向用户发送相应的反馈。
 */
@Getter
public class RateLimitException extends RuntimeException {

    private final transient Bot bot;
    // 将事件类型修改为更通用的 MessageEvent
    private final transient MessageEvent event;
    private final String message;

    public RateLimitException(String message, Bot bot, MessageEvent event) {
        super(message);
        this.message = message;
        this.bot = bot;
        this.event = event;
    }

    @Override
    public synchronized Throwable fillInStackTrace() {
        return this;
    }
}