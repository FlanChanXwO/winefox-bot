package com.github.winefoxbot.core.model.vo.webui.resp;

public record ConfigItemResponse(
        String label,
        String value,
        String description,
        int order) {
}