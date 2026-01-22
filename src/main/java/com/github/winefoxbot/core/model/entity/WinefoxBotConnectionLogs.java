package com.github.winefoxbot.core.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.github.winefoxbot.core.model.enums.common.ConnectionEventType;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * @TableName winefox_bot_connection_logs
 */
@TableName(value ="winefox_bot_connection_logs")
@Data
public class WinefoxBotConnectionLogs implements Serializable {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long botId;

    private ConnectionEventType eventType;

    @TableField(fill = FieldFill.INSERT, updateStrategy = FieldStrategy.NEVER)
    private LocalDateTime createdAt;

    @TableField(exist = false)
    private static final long serialVersionUID = 1L;
}