package com.github.winefoxbot.plugins.pixiv.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * @TableName pixiv_work
 */
@TableName(value ="pixiv_artwork")
@Data
public class PixivArtwork implements Serializable {
    @TableId(type = IdType.AUTO)
    private Integer id;

    private String illustId;

    private String authorId;
    @TableField(fill = FieldFill.INSERT,updateStrategy = FieldStrategy.NEVER)
    private LocalDateTime createdAt;
    @TableField(exist = false)
    private static final long serialVersionUID = 1L;
}