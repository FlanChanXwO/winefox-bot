package com.github.winefoxbot.core.utils;

import com.github.winefoxbot.core.annotation.plugin.ConfigItem;
import com.github.winefoxbot.core.annotation.plugin.PluginConfig;
import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.Field;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author FlanChan
 */
@Slf4j
public class ConfigReflectionUtil {

    // 简单缓存，避免频繁反射
    private static final ConcurrentHashMap<String, String> KEY_CACHE = new ConcurrentHashMap<>();

    /**
     * 根据配置类和字段名，获取完整的配置 Key (prefix.itemKey)
     * 例如：传入 AdultContentConfig.class 和 "revokeEnabled" -> 返回 "setu.revoke.enabled"
     */
    public static String getFullKey(Class<?> configClass, String fieldName) {
        String cacheKey = configClass.getName() + "#" + fieldName;
        return KEY_CACHE.computeIfAbsent(cacheKey, k -> resolveKey(configClass, fieldName));
    }

    public static PluginConfig getPluginConfig(Class<?> configClass) {
        PluginConfig pluginConfig = configClass.getAnnotation(PluginConfig.class);
        if (pluginConfig == null) {
            throw new IllegalArgumentException("类 " + configClass.getSimpleName() + " 缺少 @PluginConfig 注解");
        }
        return pluginConfig;
    }


    private static String resolveKey(Class<?> configClass, String fieldName) {
        // 1. 获取类上的 @PluginConfig (获取 prefix)
        PluginConfig pluginConfig = getPluginConfig(configClass);
        String prefix = pluginConfig.prefix();

        // 2. 获取字段上的 @ConfigItem (获取 key)
        Field field;
        try {
            field = configClass.getDeclaredField(fieldName);
        } catch (NoSuchFieldException e) {
            // 尝试去父类找 (如果字段定义在 BasePluginConfig 里)
            try {
                field = configClass.getSuperclass().getDeclaredField(fieldName);
            } catch (NoSuchFieldException ex) {
                throw new IllegalArgumentException("在类 " + configClass.getSimpleName() + " 中找不到字段: " + fieldName);
            }
        }

        ConfigItem configItem = field.getAnnotation(ConfigItem.class);
        if (configItem == null) {
            throw new IllegalArgumentException("字段 " + fieldName + " 缺少 @ConfigItem 注解");
        }

        return prefix + "." + configItem.key();
    }
}
