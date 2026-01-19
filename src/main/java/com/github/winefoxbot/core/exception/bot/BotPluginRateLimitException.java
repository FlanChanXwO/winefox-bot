package com.github.winefoxbot.core.exception.bot;

/**
 * @author FlanChan (badapple495@outlook.com)
 * @since 2026-01-19-15:56
 */
public class BotPluginRateLimitException extends BotException {
    public BotPluginRateLimitException(String message) {
        super(message);
    }
}
