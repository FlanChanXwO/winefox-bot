package com.github.winefoxbot.exception.common;

import lombok.Getter;

/**
 * @author FlanChan (badapple495@outlook.com)
 * @since 2026-01-04-0:24
 */
@Getter
public class BusinessException extends RuntimeException {
    public final Exception source;
    public BusinessException(String message, Exception source) {
        super(message);
        this.source = source;
    }
}