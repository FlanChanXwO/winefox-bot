package com.github.winefoxbot.exception.bot;

import com.mikuac.shiro.core.Bot;
import com.mikuac.shiro.dto.event.Event;
import lombok.Getter;

/**
 * @author FlanChan (badapple495@outlook.com)
 * @since 2026-01-02-19:45
 */
@Getter
public class FeatureNotEnabledException extends BaseException {


    public FeatureNotEnabledException(Bot bot, Event event, String message) {
        super(bot, event, message);
    }
}