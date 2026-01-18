package com.github.winefoxbot.core.model.vo.webui.resp;

public record SystemStatusResponse(
   String cpuUsage,
   String memoryUsage,
   String diskUsage
) {}
