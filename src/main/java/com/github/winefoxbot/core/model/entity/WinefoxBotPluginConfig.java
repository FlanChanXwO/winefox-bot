package com.github.winefoxbot.core.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.github.winefoxbot.core.model.type.PGJsonbTypeHandler;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * @author FlanChan
 * @TableName winefox_bot_app_config
 */
@TableName(value ="winefox_bot_app_config", autoResultMap = true)
@Data
public class WinefoxBotPluginConfig implements Serializable {
    @TableId(type = IdType.AUTO)
    private Integer id;

    private String configGroup;

    private String configKey;
    @TableField(typeHandler = PGJsonbTypeHandler.class)
    private Object configValue;

    private String scope;

    private String scopeId;

    private String description;

    @TableField(fill = FieldFill.INSERT,updateStrategy = FieldStrategy.NEVER)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE,updateStrategy = FieldStrategy.ALWAYS)
    private LocalDateTime updatedAt;

    @TableField(exist = false)
    private static final long serialVersionUID = 1L;
}