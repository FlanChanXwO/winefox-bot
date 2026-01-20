package com.github.winefoxbot.plugins.pixiv.model.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * @TableName pixiv_author_monitor
 */
@TableName(value ="pixiv_author_monitor")
@Data
public class PixivAuthorMonitor implements Serializable {
    private String authorId;

    private String authorName;

    private String latestIllustId;

    private LocalDateTime latestCheckedAt;

    private Boolean isMonitored;

    @TableField(fill = FieldFill.INSERT, updateStrategy = FieldStrategy.NEVER)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE, updateStrategy = FieldStrategy.ALWAYS)
    private LocalDateTime updatedAt;

    @TableField(exist = false)
    private static final long serialVersionUID = 1L;
}