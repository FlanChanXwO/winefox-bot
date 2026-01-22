package com.github.winefoxbot.core.annotation.common;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.concurrent.TimeUnit;

/**
 * 重试注解，用于标记需要自动重试的操作方法。
 * 当被标记的方法抛出指定异常时，系统将根据配置的重试策略进行重试。
 * <p>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Retry {

    /**
     * 最大重试次数 (包含第一次正常执行)
     * 默认为 3 次
     */
    int maxAttempts() default 3;

    /**
     * 重试间隔时间
     * 默认为 1000 毫秒
     */
    long delay() default 1000;

    /**
     * 时间单位
     */
    TimeUnit timeUnit() default TimeUnit.MILLISECONDS;

    /**
     * 重试失败后，抛出的最终异常的提示信息
     * 如果为空，则使用最后一次捕获的异常信息
     */
    String failureMessage() default "";

    /**
     * 重试耗尽后，抛出的自定义异常类型
     * 该异常类必须有一个接受 String 消息的构造函数
     * 默认为 RuntimeException
     */
    Class<? extends RuntimeException> failureException() default RuntimeException.class;

    /**
     * 是否启用渐进式等待 (Exponential Backoff)
     * 如果为 true，每次重试的间隔将是上一次的 2 倍
     */
    boolean exponentialBackoff() default false;

    /**
     * 触发重试的异常类型白名单
     * 默认所有 Exception 都会触发
     */
    Class<? extends Throwable>[] retryOn() default {Exception.class};
}
