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
 * @TableName pixiv_user_author_subscription_schedule_ref
 */
@TableName(value ="pixiv_user_author_subscription_schedule_ref")
@Data
public class PixivUserAuthorSubscriptionScheduleRef implements Serializable {
    @TableId(type = IdType.AUTO)
    private Integer id;

    private Long userId;

    private String authorId;

    private Long groupId;


    private Boolean isActive;

    private LocalDateTime createdAt;
    @TableField(exist = false)
    private static final long serialVersionUID = 1L;
}