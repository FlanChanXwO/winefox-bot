package com.github.winefoxbot.core.exception.bot;

import lombok.Getter;

/**
 * 机器人业务通用异常.
 * <p>
 * 替代原有的 BaseException 及其繁多的子类。
 * 不需要再携带 Bot 和 Event 对象，因为它们存在于 Context 中。
 *
 * @author FlanChan (badapple495@outlook.com)
 * @since 2026-01-19
 */
@Getter
public class BotException extends RuntimeException {

    public BotException(String message) {
        super(message);
    }

    public BotException(String message, Throwable cause) {
        super(message, cause);
    }
}
