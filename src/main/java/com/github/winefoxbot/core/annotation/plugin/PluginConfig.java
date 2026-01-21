package com.github.winefoxbot.core.annotation.plugin;

import com.github.winefoxbot.core.manager.ConfigManager;
import org.springframework.stereotype.Component;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标记一个类为插件的配置类
 * prefix: 配置项的前缀，例如 "daily_report"
 * @author FlanChan
 */

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Component
public @interface PluginConfig {
    String prefix(); // 必须指定前缀，防止冲突

    String name();   // 给人看的配置组名称，如 "酒狐日报配置"

    // 默认所有作用域都允许 (Global, Group, User)
    ConfigManager.Scope[] scopes() default {
            ConfigManager.Scope.GLOBAL,
            ConfigManager.Scope.GROUP,
            ConfigManager.Scope.USER
    };
}
