package com.github.winefoxbot.core.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.concurrent.TimeUnit;

/**
 * Redisson 分布式锁注解
 * <p>
 * 用于在方法上声明分布式锁，确保同一时间只有一个实例可以执行该方法。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RedissonLock {
    /**
     * 锁的 Key 前缀，例如 "lock:watergroup:"
     */
    String prefix();

    /**
     * SpEL 表达式，用于动态解析 Key，例如 "#groupId + ':' + #userId"
     */
    String key();

    /**
     * 等待获取锁的时间
     */
    long waitTime() default 5;

    /**
     * 锁自动释放的时间（防止死锁）
     */
    long leaseTime() default 10;

    /**
     * 时间单位
     */
    TimeUnit unit() default TimeUnit.SECONDS;
}
