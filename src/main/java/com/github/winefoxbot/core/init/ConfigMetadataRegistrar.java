package com.github.winefoxbot.core.init;

import cn.hutool.core.convert.Convert; // 【必须引入 Hutool】
import com.github.winefoxbot.core.annotation.plugin.ConfigItem;
import com.github.winefoxbot.core.annotation.plugin.PluginConfig;
import com.github.winefoxbot.core.manager.ConfigManager;
import com.github.winefoxbot.core.manager.ConfigManager.Scope;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.aop.support.AopUtils;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 自动扫描插件配置元数据，并初始化默认值到数据库（如果不存在）
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class ConfigMetadataRegistrar {

    private final ApplicationContext applicationContext;
    private final ConfigManager configManager;

    private final Map<String, Class<?>> prefixToConfigClassMap = new ConcurrentHashMap<>();

    @PostConstruct
    public void registerConfigs() {
        log.info("开始扫描插件配置定义...");

        // 获取所有带有 @PluginConfig 注解的 Bean
        Map<String, Object> configBeans = applicationContext.getBeansWithAnnotation(PluginConfig.class);

        for (Object bean : configBeans.values()) {
            // 【优化】使用 Spring 工具类获取原始类，处理 CGLIB 代理问题更稳健
            Class<?> clazz = AopUtils.getTargetClass(bean);

            PluginConfig pluginConfig = clazz.getAnnotation(PluginConfig.class);
            // 防止获取到 null (虽理论上不会，但安全起见)
            if (pluginConfig == null) {
                continue;
            }

            String prefix = pluginConfig.prefix();
            String groupName = pluginConfig.name();

            prefixToConfigClassMap.put(prefix, clazz);

            log.info("发现插件配置: [{}] prefix={}", groupName, prefix);

            // 扫描字段
            for (Field field : clazz.getDeclaredFields()) {
                if (field.isAnnotationPresent(ConfigItem.class)) {
                    ConfigItem item = field.getAnnotation(ConfigItem.class);
                    String fullKey = prefix + "." + item.key();

                    // 【修复】这里补上了第 5 个参数：field.getType()
                    initGlobalDefault(
                            fullKey,
                            item.defaultValue(),
                            item.description(),
                            groupName,
                            field.getType() // <--- 传入字段类型，用于转换
                    );
                }
            }
        }
    }

    /**
     * 根据完整的 key (如 setu.content.mode) 获取对应的配置类
     */
    public Class<?> getConfigClassByKey(String fullKey) {
        if (fullKey == null || fullKey.isBlank()) {
            return null;
        }

        // 遍历缓存的 Prefix，看哪个匹配 key 的开头
        // 比如 key 是 "setu.content.mode"，prefix 是 "setu"，匹配成功
        for (Map.Entry<String, Class<?>> entry : prefixToConfigClassMap.entrySet()) {
            String prefix = entry.getKey();
            // 匹配逻辑：key 必须以 "prefix." 开头
            if (fullKey.startsWith(prefix + ".")) {
                return entry.getValue();
            }
        }
        return null;
    }

    /**
     * 初始化或更新配置
     */
    private void initGlobalDefault(String key, String defaultValueStr, String desc, String groupName, Class<?> fieldType) {
        // 1. 检查是否存在 (使用 existsGlobal 避免 NPE)
        boolean exists = configManager.existsGlobal(key);

        if (!exists) {
            // 2. 不存在 -> 使用 Hutool 转换类型并插入
            Object typedValue = convertType(defaultValueStr, fieldType);

            if (typedValue != null) {
                configManager.set(Scope.GLOBAL, "default", key, typedValue, desc, groupName);
                log.info("初始化新配置项: {} = {}", key, typedValue);
            }
        } else {
            // 3. 已存在 -> 更新元数据 (描述和分组)，需要 ConfigManager 提供 updateMeta 方法
            configManager.updateMeta(Scope.GLOBAL, "default", key, desc, groupName);
        }
    }

    /**
     * 进行类型转换
     */
    private Object convertType(String value, Class<?> targetType) {
        try {
            return Convert.convert(targetType, value);
        } catch (Exception e) {
            log.error("无法将默认值 '{}' 转换为类型 {}", value, targetType.getName());
            return null;
        }
    }
}
