package com.github.winefoxbot.model.entity;

import com.baomidou.mybatisplus.annotation.*;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Date;
import lombok.Data;

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