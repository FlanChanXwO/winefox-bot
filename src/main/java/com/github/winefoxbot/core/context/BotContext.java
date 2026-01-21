package com.github.winefoxbot.core.context;

import com.github.winefoxbot.core.config.plugin.BasePluginConfig;
import com.mikuac.shiro.core.Bot;
import com.mikuac.shiro.dto.event.message.MessageEvent;

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
    public static final ScopedValue<BasePluginConfig> CURRENT_PLUGIN_CONFIG = ScopedValue.newInstance();


    /**
     * 在上下文中运行代码
     */
    public static void runWithContext(Bot bot, MessageEvent event, Runnable runnable) {
        ScopedValue.where(CURRENT_BOT, bot)
                .where(CURRENT_MESSAGE_EVENT, event)
                .run(runnable);
    }


    /**
     * 获取当前插件配置的便捷方法 (带类型转换)
     */
    public static <T extends BasePluginConfig> Optional<T> getPluginConfig(Class<T> clazz) {
        if (CURRENT_PLUGIN_CONFIG.isBound()) {
            BasePluginConfig config = CURRENT_PLUGIN_CONFIG.get();
            if (clazz.isInstance(config)) {
                return Optional.of(clazz.cast(config));
            }
        }
        return Optional.empty();
    }

    /**
     * 【阶段一】基础上下文初始化 (供 BotContextAspect 使用)
     */
    public static <T> T callWithContext(Bot bot, MessageEvent event, Callable<T> callable) throws Exception {
        return ScopedValue.where(CURRENT_BOT, bot)
                .where(CURRENT_MESSAGE_EVENT, event)
                // 默认绑定 None，保证 ScopedValue 始终 isBound() 为 true，避免空指针
                .where(CURRENT_PLUGIN_CONFIG, new BasePluginConfig.None())
                .call(callable::call);
    }
    /**
     * 【阶段二】特定插件配置注入 (供 PluginConfigAspect 使用)
     */
    public static <T> T callWithConfig(BasePluginConfig config, Callable<T> callable) throws Exception {
        return ScopedValue.where(CURRENT_PLUGIN_CONFIG, config)
                .call(callable::call);
    }


}
