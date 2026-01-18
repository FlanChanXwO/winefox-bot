package com.github.winefoxbot.core.model.dto.webui;

public record HealthStatus(
        String appName,
        String version
    ) {}