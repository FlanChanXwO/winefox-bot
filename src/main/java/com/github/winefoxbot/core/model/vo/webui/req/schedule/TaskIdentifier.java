package com.github.winefoxbot.core.model.vo.webui.req.schedule;

import com.github.winefoxbot.core.model.enums.common.PushTargetType;

/**
     * 基础任务定位 DTO
     */
    public record TaskIdentifier(
            Long botId,
            PushTargetType targetType,
            Long targetId,
            String taskType // 对应 taskName/taskKey
    ) {}