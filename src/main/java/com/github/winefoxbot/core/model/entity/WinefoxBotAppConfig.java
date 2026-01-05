package com.github.winefoxbot.core.model.entity;

import com.baomidou.mybatisplus.annotation.*;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Date;

import com.github.winefoxbot.core.model.type.PGJsonbListTypeHandler;
import com.github.winefoxbot.core.model.type.PGJsonbTypeHandler;
import lombok.Data;

/**
 * @TableName winefox_bot_app_config
 */
@TableName(value ="winefox_bot_app_config", autoResultMap = true)
@Data
public class WinefoxBotAppConfig implements Serializable {
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