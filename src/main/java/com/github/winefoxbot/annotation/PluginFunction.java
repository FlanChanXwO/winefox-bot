package com.github.winefoxbot.annotation;

import com.github.winefoxbot.model.enums.Permission;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标记一个方法为插件功能，用于自动生成帮助文档。
 * @author FlanChan (badapple495@outlook.com)
 * @since 2025-12-16-11:27
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface PluginFunction {
    /**
     * @return 功能的具体名称，例如 "禁言用户"
     */
    String name();
    /**
     * @return 功能的详细描述，说明其用途和用法
     */
    String description();
    /**
     * 执行此功能所需的最低权限。
     * 默认为 USER，即所有普通用户都可以使用。
     * @return 权限
     */
    Permission permission() default Permission.USER;
    /**
     * @return 触发该功能的命令描述（包括别名）。可选参数。
     */
    String[] commands() default {};

    /**
     * @return 是否自动生成该功能在帮助文档中的命令说明，不建议给复杂命令使用
     */
    boolean autoGenerateHelp() default false;

    /**
     *
     * @return 是否隐藏该功能在帮助文档
     */
    boolean hidden() default false;
}
