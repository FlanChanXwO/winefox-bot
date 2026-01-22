package com.github.winefoxbot.core.model.vo.webui.req.schedule;

import com.github.winefoxbot.core.model.enums.common.PushTargetType;

/**
     * 任务保存/更新 DTO
     * 对应 UI 中的编辑或新增
     */
    public record TaskSaveRequest(
            Long botId,
            PushTargetType targetType,
            Long targetId,
            String taskType,
            String cronExpression,
            String parameter // 对应 taskParam，通常是JSON字符串
    ) {}