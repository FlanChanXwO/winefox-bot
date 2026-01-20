package com.github.winefoxbot.core.init;

import cn.hutool.core.convert.Convert;
import com.github.winefoxbot.core.annotation.plugin.ConfigItem;
import com.github.winefoxbot.core.annotation.plugin.Plugin;
import com.github.winefoxbot.core.annotation.plugin.PluginConfig;
import com.github.winefoxbot.core.config.plugin.BasePluginConfig;
import com.github.winefoxbot.core.manager.ConfigManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.aop.support.AopUtils;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationContext;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.lang.reflect.Field;
import java.util.Map;

/**
 * 插件配置初始化器
 * <p>
 * 在应用启动完成后，扫描所有插件配置类，
 * 将代码中定义的默认值同步到数据库的 Global 作用域中。
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class PluginConfigInitializer {

    private final ApplicationContext applicationContext;
    private final ConfigManager configManager;

    @EventListener(ApplicationReadyEvent.class)
    public void initDefaultConfigs() {
        log.info("开始扫描并初始化插件默认配置...");
        
        // 1. 获取所有带有 @Plugin 注解的 Bean
        Map<String, Object> pluginBeans = applicationContext.getBeansWithAnnotation(Plugin.class);

        for (Object bean : pluginBeans.values()) {
            // 处理 AOP 代理对象获取原始类
            Class<?> userClass = AopUtils.getTargetClass(bean);
            Plugin pluginAnno = userClass.getAnnotation(Plugin.class);
            
            Class<? extends BasePluginConfig> configClass = pluginAnno.config();
            if (configClass == BasePluginConfig.None.class) {
                continue;
            }

            // 2. 解析配置类上的注解
            PluginConfig configAnno = configClass.getAnnotation(PluginConfig.class);
            if (configAnno == null) {
                log.warn("插件 [{}] 的配置类 [{}] 缺少 @PluginConfig 注解，跳过初始化", pluginAnno.name(), configClass.getName());
                continue;
            }
            
            String groupName = configAnno.name();
            String prefix = configAnno.prefix();

            // 3. 扫描配置类中的字段
            initializeFields(configClass, prefix, groupName);
        }
        log.info("插件默认配置初始化完成。");
    }

    private void initializeFields(Class<?> configClass, String prefix, String groupName) {
        // 递归处理父类字段 (比如 enabled 字段在父类 BasePluginConfig 中)
        if (configClass == null || configClass == Object.class) {
            return;
        }

        for (Field field : configClass.getDeclaredFields()) {
            ConfigItem item = field.getAnnotation(ConfigItem.class);
            if (item != null) {
                String fullKey = prefix + "." + item.key();
                String defaultValueStr = item.defaultValue();
                String description = item.description();

                // 4. 检查数据库是否存在 Global 配置
                // 我们只在 GLOBAL 作用域初始化默认值
                // 如果数据库里已经有了（哪怕值被改过），就不动它，保留用户的修改
                boolean exists = configManager.existsGlobal(fullKey);

                if (!exists) {
                    // 转换默认值类型
                    Object value = convertType(defaultValueStr, field.getType());
                    if (value != null) {
                        configManager.set(ConfigManager.Scope.GLOBAL, "default", fullKey, value, description, groupName);
                        log.debug("已初始化配置项: {} = {}", fullKey, value);
                    }
                }
            }
        }
        // 递归处理父类
        initializeFields(configClass.getSuperclass(), prefix, groupName);
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
