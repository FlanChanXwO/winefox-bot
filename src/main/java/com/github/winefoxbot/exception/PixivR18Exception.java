package com.github.winefoxbot.exception;

/**
 * @author FlanChan (badapple495@outlook.com)
 * @since 2025-12-16-21:35
 */
public class PixivR18Exception extends RuntimeException {
    public PixivR18Exception() {
        super("该图片为 R18 作品，不可访问");
    }
}