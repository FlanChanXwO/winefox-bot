package com.github.winefoxbot.core.model.vo.webui.resp;

import com.github.winefoxbot.core.model.enums.common.PushTargetType;

public record TaskTypeResponse(String key, String name, PushTargetType targetType, String description, String paramExample) {}