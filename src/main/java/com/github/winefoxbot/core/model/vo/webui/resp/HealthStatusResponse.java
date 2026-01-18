package com.github.winefoxbot.core.model.vo.webui.resp;

public record HealthStatusResponse(
        String appName,
        String version
    ) {}