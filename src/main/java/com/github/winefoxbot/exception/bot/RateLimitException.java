package com.github.winefoxbot.exception.bot;

import com.mikuac.shiro.core.Bot;
import com.mikuac.shiro.dto.event.Event;
import lombok.Getter;

/**
 * 自定义限流异常
 * <p>
 * 当请求速率超过注解 @Limit 定义的阈值时，由 LimitAspect 抛出。
 * 此异常包含触发限流时的上下文信息，如 Bot 实例、消息事件和提示消息，
 * 以便全局异常处理器能够向用户发送相应的反馈。
 */
@Getter
public class RateLimitException extends BaseException {


    public RateLimitException(Bot bot, Event event, String message) {
        super(bot, event, message);
    }

    @Override
    public synchronized Throwable fillInStackTrace() {
        return this;
    }
}