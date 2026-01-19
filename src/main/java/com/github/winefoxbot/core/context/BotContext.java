package com.github.winefoxbot.core.context;

import com.mikuac.shiro.core.Bot;
import com.mikuac.shiro.dto.event.message.MessageEvent;

import java.util.concurrent.Callable;

/**
 * Bot 上下文管理类
 *
 * @author FlanChan
 */
public final class BotContext {

    private BotContext() {
        // 私有构造函数，防止实例化
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }

    public static final ScopedValue<Bot> CURRENT_BOT = ScopedValue.newInstance();
    public static final ScopedValue<MessageEvent> CURRENT_MESSAGE_EVENT = ScopedValue.newInstance();

    /**
     * 在上下文中运行代码
     */
    public static void runWithContext(Bot bot, MessageEvent event, Runnable runnable) {
        ScopedValue.where(CURRENT_BOT, bot)
                .where(CURRENT_MESSAGE_EVENT, event)
                .run(runnable);
    }

    /**
     * 在上下文中运行有返回值的代码
     */
    public static <T> T callWithContext(Bot bot, MessageEvent event, Callable<T> callable) throws Exception {
        return ScopedValue.where(CURRENT_BOT, bot)
                .where(CURRENT_MESSAGE_EVENT, event)
                .call(callable::call);
    }
}
