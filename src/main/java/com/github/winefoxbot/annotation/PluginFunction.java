package com.github.winefoxbot.annotation;

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
     * @return 功能所属的分组，例如 "管理功能" 或 "娱乐功能"
     */
    String group();

    /**
     * @return 功能的具体名称，例如 "禁言用户"
     */
    String name();
    /**
     * @return 功能的详细描述，说明其用途和用法
     */
    String description();
    /**
     * @return 该功能所需的权限级别，例如 "ADMIN"、"MODERATOR"、"USER" 等
     */
    String permission() default "普通用户";
    /**
     * @return 触发该功能的命令（包括别名）。可选参数。
     */
    String[] commands() default {};

    /**
     * @return 功能在图中的优先级，数值越大优先级越高，默认为0
     */
    int priority() default 0;
}
