package com.github.winefoxbot.model.entity;

import com.baomidou.mybatisplus.annotation.*;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Date;

import com.github.winefoxbot.model.enums.SessionType;
import lombok.Data;

/**
 * @TableName setu_config
 */
@TableName(value ="setu_config")
@Data
public class SetuConfig implements Serializable {
    @TableId(type = IdType.AUTO)
    private Integer id;

    private Long sessionId;

    private Integer maxRequestInSession;

    private SessionType sessionType;

    private Boolean r18Enabled;

    private Boolean autoRevoke;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    private static final long serialVersionUID = 1L;
}