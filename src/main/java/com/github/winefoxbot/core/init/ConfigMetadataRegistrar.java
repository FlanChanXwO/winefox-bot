package com.github.winefoxbot.core.init;


import com.github.winefoxbot.core.annotation.plugin.ConfigItem;
import com.github.winefoxbot.core.annotation.plugin.PluginConfig;
import com.github.winefoxbot.core.manager.ConfigManager;
import com.github.winefoxbot.core.manager.ConfigManager.Scope;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.lang.reflect.Field;
import java.util.Map;

/**
 * 自动扫描插件配置元数据，并初始化默认值到数据库（如果不存在）
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class ConfigMetadataRegistrar {

    private final ApplicationContext applicationContext;
    private final ConfigManager configManager;

    @PostConstruct
    public void registerConfigs() {
        log.info("开始扫描插件配置定义...");

        // 获取所有带有 @PluginConfig 注解的 Bean
        Map<String, Object> configBeans = applicationContext.getBeansWithAnnotation(PluginConfig.class);

        for (Object bean : configBeans.values()) {
            Class<?> clazz = bean.getClass();
            // 处理 AOP 代理情况，获取原始类
            if (clazz.getName().contains("SpringCGLIB")) {
                clazz = clazz.getSuperclass();
            }

            PluginConfig pluginConfig = clazz.getAnnotation(PluginConfig.class);
            String prefix = pluginConfig.prefix();
            String groupName = pluginConfig.name(); // 插件名作为分组名

            log.info("发现插件配置: [{}] prefix={}", groupName, prefix);

            // 扫描字段
            for (Field field : clazz.getDeclaredFields()) {
                if (field.isAnnotationPresent(ConfigItem.class)) {
                    ConfigItem item = field.getAnnotation(ConfigItem.class);
                    String fullKey = prefix + "." + item.key();

                    // 这里我们做两件事：
                    // 1. (可选) 保存元数据到一张新表 `sys_config_metadata`，供 WebUI 展示“有哪些配置可配”
                    // 2. 初始化全局默认值到 `winefox_bot_app_config`

                    initGlobalDefault(fullKey, item.defaultValue(), item.description(), groupName);
                }
            }
        }
    }

    private void initGlobalDefault(String key, String defaultValue, String desc, String groupName) {
        // 尝试获取全局配置
        var existing = configManager.getGlobalConfigOrDefault(key, null);

        if (existing == null) {
            // 如果不存在，写入默认值
            // 注意：这里需要根据字段类型做简单转换，为了演示默认存 String
            configManager.set(Scope.GLOBAL, "default", key, defaultValue, desc, groupName);
            log.info("初始化配置项: {} = {}", key, defaultValue);
        } else {
            // 如果存在，可以考虑更新 description (因为代码里的描述可能更新了)
            // 这一步取决于业务需求，是否允许代码覆盖数据库的描述
            configManager.set(Scope.GLOBAL, "default", key, existing, desc, groupName);
        }
    }
}
