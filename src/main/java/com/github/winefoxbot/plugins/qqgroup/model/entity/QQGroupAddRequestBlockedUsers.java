package com.github.winefoxbot.plugins.qqgroup.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * @TableName qq_group_add_request_blocked_users
 */
@TableName(value ="qq_group_add_request_blocked_users")
@Data
public class QQGroupAddRequestBlockedUsers implements Serializable {
    @TableId(type = IdType.AUTO)
    private Integer id;

    private Long groupId;

    private Long userId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(exist = false)
    private static final long serialVersionUID = 1L;
}