package com.github.winefoxbot.core.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Date;
import lombok.Data;

/**
 * @TableName shiro_event_subscription
 */
@TableName(value ="shiro_event_subscription")
@Data
public class ShiroEventSubscription implements Serializable {
    @TableId(type = IdType.AUTO)
    private Integer id;

    private Long botId;

    private String eventType;

    private String eventKey;

    private String targetType;

    private Long targetId;

    private Long mentionUserId;

    private LocalDateTime createdAt;

    @TableField(exist = false)
    private static final long serialVersionUID = 1L;
}