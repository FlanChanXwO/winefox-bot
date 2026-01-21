package com.github.winefoxbot.plugins.setu.model.dto;

import java.net.URI;

/**
 * 图片源的封装类，用于统一处理字节数组和URI。
 */
public record ImageSource(
        byte[] bytes,
        URI uri) {
    ImageSource(URI uri) {
        this(null, uri);
    }

    ImageSource(byte[] bytes) {
        this(bytes, null);
    }

    boolean isBytes() {
        return bytes != null;
    }
}