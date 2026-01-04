package com.github.winefoxbot.core.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Shiro 使用者實體
 */
@Data
@TableName("shiro_users")
public class ShiroUser {

    /**
     * 使用者 ID
     */
    @TableId(value = "user_id", type = IdType.INPUT)
    private Long userId;

    /**
     * 暱稱
     */
    private String nickname;

    /**
     * 頭像 URL
     */
    private String avatarUrl;

    /**
     * 最後更新時間
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime lastUpdated;

}