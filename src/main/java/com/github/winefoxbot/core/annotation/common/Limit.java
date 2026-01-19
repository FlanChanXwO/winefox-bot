package com.github.winefoxbot.core.annotation.common;

import com.github.winefoxbot.core.exception.bot.BotPluginRateLimitException;

import java.lang.annotation.*;

/**
 * 声明式接口限流注解
 * <p>
 * 可以作用于类或方法上。
 * - 当作用于类上时，该类下的所有方法共享同一个全局限流器，并且每个方法也会应用相同的用户/群组限流规则。
 * - 当作用于方法上时，仅对当前方法生效。
 * - 方法上的注解会覆盖类上的注解配置。
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Limit {

    /**
     * 全局限流：允许在指定时间窗口内访问的总次数。
     *
     * @return 允许的访问总次数
     */
    int globalPermits() default 20;

    /**
     * 用户/群组限流：允许单个用户或群组在指定时间窗口内访问的次数。
     * <p>
     * - 在群聊中，此限制针对群组ID (groupId)。
     * - 在私聊中，此限制针对用户ID (userId)。
     * - 设置为0或负数表示不启用用户/群组级别的限流。
     *
     * @return 单个用户/群组允许的访问次数
     */
    int userPermits() default 5;

    /**
     * 限流时间窗口，单位为秒。
     *
     * @return 时间窗口大小（秒）
     */
    int timeInSeconds() default 60;

    /**
     * 达到限流条件时，通过Bot发送给用户的提示信息。
     *
     * @return 提示信息文本
     */
    String message() default "";

    /**
     * 发送限流提示的最小时间间隔（单位：秒）。
     * 0 表示每次触发限流都发送提示。
     * 大于 0 的值 n 表示：触发限流后，n 秒内即使再次被限流，也不会重复发送提示。
     * 默认为 60 秒。
     * @return 通知间隔时间（秒）
     */
    int notificationIntervalSeconds() default 60;

    /**
     * 限流触发时要抛出的异常类。
     * 该异常将被全局异常处理器捕获，用于中断当前操作并向用户发送提示。
     *
     * @return 异常类
     */
    Class<? extends Throwable> throwsException() default BotPluginRateLimitException.class;

}