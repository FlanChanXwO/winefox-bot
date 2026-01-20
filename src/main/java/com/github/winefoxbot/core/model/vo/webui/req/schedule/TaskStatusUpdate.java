package com.github.winefoxbot.core.model.vo.webui.req.schedule;

import com.github.winefoxbot.core.model.enums.PushTargetType;

/**
     * 状态更新 DTO
     */
    public record TaskStatusUpdate(
            Long botId,
            PushTargetType targetType,
            Long targetId,
            String taskType,
            Boolean enable
    ) {}