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
import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * 插件配置初始化器
 * <p>
 * 在应用启动完成后，扫描所有插件配置类，
 * 将代码中定义的默认值同步到数据库的 Global 作用域中。
 * <p>
 * 逻辑更新：
 * 1. 优先使用 Spring 配置文件 (yaml/properties) 中的值（如果存在且不等于代码默认值）。
 * 2. 如果数据库中已存在配置：
 *    - 如果数据库值等于代码默认值，但 Spring 配置有新值 -> 更新数据库为 Spring 配置值。
 *    - 否则保留数据库值（可能是用户手动修改的）。
 * 3. 如果数据库不存在配置 -> 插入（优先用 Spring 配置值，否则用代码默认值）。
 * 4. 仅当 @PluginConfig 指定的 scopes 包含 GLOBAL 时，才进行初始化。
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

            // 检查是否包含 GLOBAL 作用域
            boolean hasGlobalScope = Arrays.asList(configAnno.scopes()).contains(ConfigManager.Scope.GLOBAL);
            if (!hasGlobalScope) {
                log.debug("插件 [{}] 的配置类 [{}] 未启用 GLOBAL 作用域，跳过全局配置初始化", pluginAnno.name(), configClass.getName());
                continue;
            }
            
            String prefix = configAnno.prefix();
            
            // 尝试从 Spring 容器中获取配置 Bean 实例（如果存在）
            // 这样可以直接拿到 Spring 注入后的值（即 yaml 中的值）
            Object springConfigBean = null;
            try {
                springConfigBean = applicationContext.getBean(configClass);
            } catch (Exception e) {
                // 忽略，可能没有注册为 Bean，或者多例等情况
                // 如果配置类没有被 @Component 或 @ConfigurationProperties 扫描到，这里可能拿不到
                // 但通常插件配置类应该被注册
            }

            // 3. 扫描配置类中的字段
            initializeFields(configClass, prefix, springConfigBean);
        }
        log.info("插件默认配置初始化完成。");
    }

    private void initializeFields(Class<?> configClass, String prefix, Object springConfigBean) {
        // 递归处理父类字段 (比如 enabled 字段在父类 BasePluginConfig 中)
        if (configClass == null || configClass == Object.class) {
            return;
        }

        for (Field field : configClass.getDeclaredFields()) {
            ConfigItem item = field.getAnnotation(ConfigItem.class);
            if (item != null) {
                String fullKey = prefix + "." + item.key();
                String codeDefaultValueStr = item.defaultValue();
                String description = item.description();

                // 代码中的默认值
                Object codeDefaultValue = convertType(codeDefaultValueStr, field.getType());

                // Spring 配置中的值 (YAML/Properties)
                Object springConfigValue = null;
                if (springConfigBean != null) {
                    try {
                        field.setAccessible(true);
                        springConfigValue = field.get(springConfigBean);
                    } catch (IllegalAccessException e) {
                        log.warn("无法读取配置 Bean 字段: {}", field.getName());
                    }
                }

                // 确定“应该”使用的初始值：优先 Spring 配置，否则代码默认值
                // 注意：这里假设如果 Spring 配置值不为 null 且不等于代码默认值，就是用户配置了
                // 但如果 Spring Bean 初始化时也是用的默认值，那么 springConfigValue 可能等于 codeDefaultValue
                // 这种情况下，视为没有特殊配置，依然使用 codeDefaultValue
                Object targetInitialValue = codeDefaultValue;
                boolean hasSpringConfig = false;

                if (springConfigValue != null && !Objects.equals(springConfigValue, codeDefaultValue)) {
                    targetInitialValue = springConfigValue;
                    hasSpringConfig = true;
                }

                // 4. 检查数据库是否存在 Global 配置
                Optional<Object> dbValueOpt = configManager.get(fullKey, ConfigManager.Scope.GLOBAL, "default", Object.class);

                if (dbValueOpt.isPresent()) {
                    Object dbValue = dbValueOpt.get();
                    // 数据库已存在
                    // 核心逻辑：如果数据库值 == 代码默认值，说明用户可能没改过，只是初始化进去的
                    // 这时如果 Spring 配置有新值，应该更新数据库
                    // 注意类型转换比较
                    Object convertedDbValue = Convert.convert(field.getType(), dbValue);
                    
                    if (hasSpringConfig && Objects.equals(convertedDbValue, codeDefaultValue)) {
                        // 数据库存的是旧的默认值，现在 yaml 里有新配置，更新它
                        configManager.set(ConfigManager.Scope.GLOBAL, "default", fullKey, targetInitialValue, description, prefix);
                        log.info("检测到 Spring 配置变更，更新数据库配置: {} -> {}", fullKey, targetInitialValue);
                    } else {
                        // 数据库值与默认值不同（可能是用户改过），或者没有 Spring 配置变更 -> 仅更新元数据
                        configManager.updateMeta(ConfigManager.Scope.GLOBAL, "default", fullKey, description, prefix);
                    }
                } else {
                    // 数据库不存在 -> 插入
                    if (targetInitialValue != null) {
                        configManager.set(ConfigManager.Scope.GLOBAL, "default", fullKey, targetInitialValue, description, prefix);
                        log.debug("初始化配置项: {} = {}", fullKey, targetInitialValue);
                    }
                }
            }
        }
        // 递归处理父类
        initializeFields(configClass.getSuperclass(), prefix, springConfigBean);
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
