package com.github.winefoxbot.exception.bot;

import com.mikuac.shiro.core.Bot;
import com.mikuac.shiro.dto.event.Event;
import lombok.Getter;

/**
 * @author FlanChan (badapple495@outlook.com)
 * @since 2026-01-02-19:42
 */
@Getter
public class PermissionDeniedException extends BaseException {


    public PermissionDeniedException(Bot bot, Event event, String message, Exception e) {
        super(bot, event, message, e);
    }
}