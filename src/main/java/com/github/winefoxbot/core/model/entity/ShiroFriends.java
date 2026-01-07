package com.github.winefoxbot.core.model.entity;

import com.baomidou.mybatisplus.annotation.*;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Date;
import lombok.Data;

/**
 * @TableName shiro_friends
 */
@TableName(value ="shiro_friends")
@Data
public class ShiroFriends implements Serializable {
    @TableId(type = IdType.INPUT)
    private Long botId;
    private Long friendId;

    private String nickname;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime lastUpdated;

    @TableField(exist = false)
    private static final long serialVersionUID = 1L;
}