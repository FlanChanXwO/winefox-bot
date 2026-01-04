package com.github.winefoxbot.core.exception.bot;

import com.mikuac.shiro.core.Bot;
import com.mikuac.shiro.dto.event.Event;
import lombok.Getter;

/**
 * @author FlanChan (badapple495@outlook.com)
 * @since 2026-01-04-0:27
 */
@Getter
public abstract class BaseException extends RuntimeException {
    private final transient Bot bot;
    private final transient Event event;
    private final transient Exception source;
    public BaseException(Bot bot, Event event, String message, Exception source) {
        super(message);
        this.bot = bot;
        this.event = event;
        this.source = source;
    }
}