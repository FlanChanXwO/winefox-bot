package com.github.winefoxbot.core.model.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 统一的群组推送日程配置
 * @TableName group_push_schedule
 */
@TableName(value ="group_push_schedule")
@Data
public class GroupPushSchedule implements Serializable {
    @TableId(type = IdType.AUTO)
    private Integer id;

    private Long groupId;

    /**
     * 任务类型 e.g. 'WATER_GROUP_STAT', 'PIXIV_RANK'
     */
    private String taskType;

    /**
     * 任务参数 e.g. 'daily', 'weekly' (可选)
     */
    private String taskParam;

    /**
     * Cron表达式
     */
    private String cronExpression;

    /**
     * 描述
     */
    private String description;

    /**
     * 是否启用
     */
    private Boolean isEnabled;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    private static final long serialVersionUID = 1L;
}
