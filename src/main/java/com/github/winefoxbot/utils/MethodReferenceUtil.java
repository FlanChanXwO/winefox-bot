package com.github.winefoxbot.utils;

import java.io.Serializable;
import java.lang.invoke.SerializedLambda;
import java.lang.reflect.Method;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;

/**
 * 一个通过方法引用安全地获取方法名的工具类。
 * 新版本：专为 ClassName::instanceMethod 形式设计。
 */
public final class MethodReferenceUtil {

    // --- 1. 定义可序列化的 BiConsumer 和 BiFunction ---
    @FunctionalInterface
    public interface SBiConsumer<T, U> extends BiConsumer<T, U>, Serializable {}

    @FunctionalInterface
    public interface SBiFunction<T, U, R> extends BiFunction<T, U, R>, Serializable {}

    /**
     * [重载1] 从一个无返回值的实例方法引用中获取方法名。
     * T: 方法所在的类
     * U: 方法的参数类型
     * @param biConsumer 方法引用, e.g., PixivRankPushExecutor::execute
     * @return 方法名字符串
     */
    public static <T, U> String getMethodName(SBiConsumer<T, U> biConsumer) {
        return resolveMethodName(biConsumer);
    }

    /**
     * [重载2] 从一个有返回值的实例方法引用中获取方法名。
     * T: 方法所在的类
     * U: 方法的参数类型
     * R: 方法的返回类型
     * @param biFunction 方法引用, e.g., String::substring
     * @return 方法名字符串
     */
    public static <T, U, R> String getMethodName(SBiFunction<T, U, R> biFunction) {
        return resolveMethodName(biFunction);
    }

    // 内部解析逻辑保持不变，它只关心传入的是否为 Serializable
    private static String resolveMethodName(Serializable lambda) {
        try {
            Method method = lambda.getClass().getDeclaredMethod("writeReplace");
            method.setAccessible(true);
            SerializedLambda serializedLambda = (SerializedLambda) method.invoke(lambda);
            return serializedLambda.getImplMethodName();
        } catch (Exception e) {
            throw new IllegalArgumentException("无法从给定的Lambda表达式中解析方法名。", e);
        }
    }
}
