package com.github.winefoxbot.plugins.pixiv.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * @TableName pixiv_rank_push_schedule
 */
@TableName(value ="pixiv_rank_push_schedule")
@Data
public class PixivRankPushSchedule implements Serializable {
    @TableId(type = IdType.AUTO)
    private Integer id;

    private Long groupId;

    private String rankType;

    private String cronSchedule;

    private String description;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
    @TableField(exist = false)
    private static final long serialVersionUID = 1L;
}