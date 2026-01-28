package com.github.winefoxbot.core.context;

import com.github.winefoxbot.core.config.plugin.BasePluginConfig;
import com.github.winefoxbot.plugins.fortune.config.FortunePluginConfig;
import com.mikuac.shiro.core.Bot;
import com.mikuac.shiro.dto.event.message.MessageEvent;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
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

    // 存放当前正在处理的 Bot 对象
    public static final ScopedValue<Bot> CURRENT_BOT = ScopedValue.newInstance();
    // 存放当前正在处理的消息事件
    public static final ScopedValue<MessageEvent> CURRENT_MESSAGE_EVENT = ScopedValue.newInstance();
    // 存放当前正在执行的插件的配置对象
    public static final ScopedValue<BasePluginConfig> CURRENT_PLUGIN_CONFIN = ScopedValue.newInstance();


    /**
     * 在上下文中运行代码
     */
    public static void runWithContext(Bot bot, MessageEvent event, Runnable runnable) {
        ScopedValue.where(CURRENT_BOT, bot)
                .where(CURRENT_MESSAGE_EVENT, event)
                .run(runnable);
    }


    /**
     * 在上下文中运行代码
     */
    public static void runWithContext(Bot bot, MessageEvent event, BasePluginConfig config, Runnable runnable) {
        ScopedValue.where(CURRENT_BOT, bot)
                .where(CURRENT_MESSAGE_EVENT, event)
                .where(CURRENT_PLUGIN_CONFIN, config)
                .run(runnable);
    }


    public static <T> T callWithContext(Bot bot, MessageEvent event, Callable<T> callable) throws Exception {
        return ScopedValue.where(CURRENT_BOT, bot)
                .where(CURRENT_MESSAGE_EVENT, event)
                .where(CURRENT_PLUGIN_CONFIN, new BasePluginConfig.None())
                .call(callable::call);
    }


    public static <T> T callWithConfig(BasePluginConfig config, Callable<T> callable) throws Exception {
        return ScopedValue.where(CURRENT_PLUGIN_CONFIN, config)
                .call(callable::call);
    }
}
