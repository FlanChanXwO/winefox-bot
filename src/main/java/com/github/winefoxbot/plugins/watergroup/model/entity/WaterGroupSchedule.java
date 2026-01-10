package com.github.winefoxbot.plugins.watergroup.model.entity;

import lombok.Data;

import java.time.LocalTime;
/**
 * @author FlanChan (badapple495@outlook.com)
 * @since 2025-12-24-11:53
 *
 * 这是一个DTO，用于在插件和核心调度服务之间传递数据。
 * 旧的数据库表已废弃，统一使用 group_push_schedule。
 */
@Data
public class WaterGroupSchedule {
    private Long id;

    private Long groupId;
    private LocalTime time;
}