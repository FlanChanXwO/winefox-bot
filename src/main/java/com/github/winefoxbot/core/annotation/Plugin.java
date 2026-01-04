package com.github.winefoxbot.core.annotation;

import com.github.winefoxbot.core.model.enums.Permission;

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
@Target(ElementType.TYPE)
public @interface Plugin {
    /**
     * @return 功能组的具体名称，例如 "管理功能" 或 "娱乐功能"
     */
    String name();
    /**
     * @return 功能组的详细描述，说明其用途和用法
     */
    String description();

    /**
     * @return 功能组的排序值，数值越小越靠前显示
     */
    int order() default Integer.MAX_VALUE;

    /**
     * 功能组的图标
     * 从类加载路径读取
     * @return 图标路径
     */
    String iconPath() default "icon/默认图标.png";
    /**
     * 执行此功能组下所有功能所需的最低权限。如果功能单独标记了权限，则以功能的权限为准。
     * 默认为 USER，即所有普通用户都可以使用。
     * @return 权限
     */
    Permission permission() default Permission.USER;

    /**
     * @return 是否隐藏所有功能在帮助文档
     */
    boolean hidden() default false;
}
