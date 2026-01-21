package com.github.winefoxbot.core.model.entity;

import com.baomidou.mybatisplus.annotation.*;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Date;
import lombok.Data;

/**
 * @TableName winefox_bot_plugin_invoke_stats
 */
@TableName(value ="winefox_bot_plugin_invoke_stats")
@Data
public class WinefoxBotPluginInvokeStats implements Serializable {

    @TableId(type = IdType.INPUT)
    private String pluginClassName;

    private LocalDate statDate;

    private Long callCount;

    @TableField(fill = FieldFill.INSERT_UPDATE, updateStrategy = FieldStrategy.ALWAYS)
    private LocalDateTime lastUpdateTime;

    @TableField(exist = false)
    private static final long serialVersionUID = 1L ;
}