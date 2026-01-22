package com.github.winefoxbot.core.context;

import com.github.winefoxbot.core.config.plugin.BasePluginConfig;
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
    public static final ScopedValue<Map<Class<? extends BasePluginConfig>, BasePluginConfig>> CURRENT_PLUGIN_CONFINS = ScopedValue.newInstance();


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
                .where(CURRENT_PLUGIN_CONFINS, Map.of(config.getClass(), config))
                .run(runnable);
    }


    /**
     * 获取当前插件配置的便捷方法
     */
    public static <T extends BasePluginConfig> Optional<T> getPluginConfig(Class<T> clazz) {
        if (CURRENT_PLUGIN_CONFINS.isBound()) {
            Map<Class<? extends BasePluginConfig>, BasePluginConfig> configMap = CURRENT_PLUGIN_CONFINS.get();
            // 从 Map 中获取指定类型的配置
            BasePluginConfig config = configMap.get(clazz);
            if (clazz.isInstance(config)) {
                return Optional.of(clazz.cast(config));
            }
        }
        return Optional.empty();
    }


    /**
     * 获取当前插件配置的便捷方法
     */
    @SuppressWarnings("unchecked")
    public static <T extends BasePluginConfig> T getFirstPluginConfig() {
        if (CURRENT_PLUGIN_CONFINS.isBound()) {
            Map<Class<? extends BasePluginConfig>, BasePluginConfig> configMap = CURRENT_PLUGIN_CONFINS.get();
            return (T) configMap.values().stream().findFirst().get();
        }
        throw new IllegalStateException("No plugin configuration found in the current context.");
    }



    public static <T> T callWithContext(Bot bot, MessageEvent event, Callable<T> callable) throws Exception {
        return ScopedValue.where(CURRENT_BOT, bot)
                .where(CURRENT_MESSAGE_EVENT, event)
                .where(CURRENT_PLUGIN_CONFINS, Map.of(BasePluginConfig.class, new BasePluginConfig.None()))
                .call(callable::call);
    }

    public static <T> T callWithConfigs(Map<Class<? extends BasePluginConfig>, BasePluginConfig> newConfigs, Callable<T> callable) throws Exception {
        // 1. 获取当前已有的配置（如果有的话，实现嵌套继承）
        Map<Class<? extends BasePluginConfig>, BasePluginConfig> currentMap = CURRENT_PLUGIN_CONFINS.isBound()
                ? CURRENT_PLUGIN_CONFINS.get()
                : Collections.emptyMap();

        // 2. 合并配置：新配置覆盖旧配置，或者追加
        Map<Class<? extends BasePluginConfig>, BasePluginConfig> mergedMap = new HashMap<>(currentMap);
        mergedMap.putAll(newConfigs);

        // 3. 绑定新的不可变 Map
        return ScopedValue.where(CURRENT_PLUGIN_CONFINS, Map.copyOf(mergedMap))
                .call(callable::call);
    }


    public static <T> T callWithConfig(BasePluginConfig config, Callable<T> callable) throws Exception {
        return ScopedValue.where(CURRENT_PLUGIN_CONFINS, Map.of(config.getClass(), config))
                .call(callable::call);
    }


}
