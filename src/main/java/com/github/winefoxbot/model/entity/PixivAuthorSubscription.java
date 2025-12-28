package com.github.winefoxbot.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Date;
import lombok.Data;

/**
 * @TableName pixiv_author_subscription
 */
@TableName(value ="pixiv_author_subscription")
@Data
public class PixivAuthorSubscription implements Serializable {
    @TableId(type = IdType.AUTO)
    private String authorId;

    private String authorName;

    private Boolean isActive;

    private LocalDateTime lastCheckedAt;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
    @TableField(exist = false)
    private static final long serialVersionUID = 1L;
}