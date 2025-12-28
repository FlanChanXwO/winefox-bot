package com.github.winefoxbot.model.entity;

import com.baomidou.mybatisplus.annotation.*;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Date;
import lombok.Data;

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