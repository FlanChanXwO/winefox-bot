package com.github.winefoxbot.plugins.pixiv.model.entity;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 这是一个DTO，用于在插件和核心调度服务之间传递数据。
 * 旧的数据库表已废弃，统一使用 group_push_schedule。
 */
@Data
public class PixivRankPushSchedule implements Serializable {
    private Integer id;

    private Long groupId;

    private String rankType;

    private String cronSchedule;

    private String description;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    private static final long serialVersionUID = 1L;
}