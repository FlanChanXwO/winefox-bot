package com.github.winefoxbot.core.model.entity;

import com.baomidou.mybatisplus.annotation.*;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Date;
import lombok.Data;

/**
 * @TableName winefox_bot_plugin_meta
 */
@TableName(value ="winefox_bot_plugin_meta")
@Data
public class WinefoxBotPluginMeta implements Serializable {
    @TableId(type = IdType.INPUT)
    private String className;

    private String displayName;

    private String description;

    private String iconPath;

    private Boolean isActive;
    @TableField(fill = FieldFill.INSERT_UPDATE, updateStrategy = FieldStrategy.ALWAYS)
    private LocalDateTime lastActiveTime;
    @TableField(fill = FieldFill.INSERT, updateStrategy = FieldStrategy.NEVER)
    private LocalDateTime createdAt;
    @TableField(exist = false)
    private static final long serialVersionUID = 1L;
}