package com.github.winefoxbot.core.model.dto.webui;

public record SystemStatusDTO (
   String cpuUsage,
   String memoryUsage,
   String diskUsage
) {}
