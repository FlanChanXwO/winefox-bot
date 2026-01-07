package com.github.winefoxbot.core.model.entity;

import com.baomidou.mybatisplus.annotation.*;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Date;
import lombok.Data;

/**
 * @TableName shiro_bots
 */
@TableName(value ="shiro_bots")
@Data
public class ShiroBots implements Serializable {
    @TableId(type = IdType.INPUT)
    private Long botId;

    private String nickname;

    private String avatarUrl;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime lastUpdated;
    @TableField(exist = false)
    private static final long serialVersionUID = 1L;
}