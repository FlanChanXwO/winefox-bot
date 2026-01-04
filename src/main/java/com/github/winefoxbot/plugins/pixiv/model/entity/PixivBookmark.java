package com.github.winefoxbot.plugins.pixiv.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.github.winefoxbot.plugins.pixiv.model.enums.PixivRatingLevel;
import com.github.winefoxbot.core.model.type.GenericEnumTypeHandler;
import com.github.winefoxbot.core.model.type.PGJsonbListTypeHandler;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * @TableName pixiv_bookmark
 */
@TableName(value ="pixiv_bookmark", autoResultMap = true)
@Data
public class PixivBookmark implements Serializable {
    @TableId(type = IdType.INPUT)
    private String id;
    private String trackedUserId;
    private String title;
    private Integer illustType;
    @TableField(typeHandler = GenericEnumTypeHandler.class)
    private PixivRatingLevel xRestrict;

    private Integer slLevel;
    private String description;
    private Integer aiType;
    private String authorId;

    private String authorName;

    private String imageUrl;

    private Integer width;

    private Integer height;

    private Integer pageCount;

    /**
     * 作品标签列表
     */
    @TableField(typeHandler = PGJsonbListTypeHandler.class) // 使用我们自定义的 Handler
    private List<String> tags;

    private LocalDateTime pixivCreateDate;
    private LocalDateTime pixivUpdateDate;

    @TableField(fill = FieldFill.INSERT, updateStrategy = FieldStrategy.NEVER)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableField(exist = false)
    private static final long serialVersionUID = 1L;
}