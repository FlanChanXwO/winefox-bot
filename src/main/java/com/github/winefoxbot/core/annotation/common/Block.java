package com.github.winefoxbot.core.annotation.common;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 自定义注解，用于标记需要进行会话上下文检查的插件方法。
 * 当用户处于一个特定的会话状态（例如，等待翻页输入）时，
 * 被此注解标记的方法将不会被执行。
 * <p>
 * 这主要用于避免通用聊天插件（如 AI 回复）响应
 * 为特定插件（如磁力搜索）准备的指令（如“下一页”）。
 * @author FlanChan
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Block {
}