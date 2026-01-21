package com.github.winefoxbot.core.annotation.plugin;

import com.github.winefoxbot.core.config.plugin.BasePluginConfig;
import com.github.winefoxbot.core.model.enums.Permission;
import com.mikuac.shiro.annotation.common.Shiro;
import org.springframework.stereotype.Component;

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
@Shiro
@Component
public @interface Plugin {
    /**
     * @return 功能组的具体名称，例如 "管理功能" 或 "娱乐功能"
     */
    String name();

    /**
     * @return 功能组的排序值，数值越小越靠前显示
     */
    int order() default Integer.MAX_VALUE;

    String description() default "暂无描述";

    String version() default "1.0.0";

    String author() default "FlanChanXwO"; // 【新增】作者

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

    /**
     * 是否为内置插件，内置插件无法卸载
     */
    boolean builtIn() default true;

    /**
     * 指定该插件对应的配置类
     * 必须继承 BasePluginConfig
     * 默认为 Void.class 表示该插件没有配置
     */
    Class<? extends BasePluginConfig> config() default BasePluginConfig.None.class;

    /**
     * 是否允许禁用此插件
     */
    boolean canDisable() default true;
}
