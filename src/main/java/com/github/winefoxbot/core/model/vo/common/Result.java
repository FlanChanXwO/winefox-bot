package com.github.winefoxbot.core.model.vo.common;

import java.time.Instant;

/**
 * 统一 API 响应结构
 *
 * @param <T> 数据载体类型
 */
public record Result<T>(
    boolean success,
    String message,
    T data,
    long timestamp
) {

    /**
     * 快速构建成功响应
     */
    public static <T> Result<T> success(T data) {
        return new Result<>(
            true, 
            "ok",
            data, 
            Instant.now().toEpochMilli()
        );
    }

    /**
     * 快速构建失败响应
     */
    public static <T> Result<T> error(String message) {
        return new Result<>(
            false, 
            message,
            null, 
            Instant.now().toEpochMilli()
        );
    }
}
