package com.github.winefoxbot.core.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Shiro 羣組實體
 */
@Data
@TableName("shiro_groups")
public class ShiroGroup {

    /**
     * 羣組 ID
     */
    @TableId(value = "group_id", type = IdType.INPUT)
    private Long groupId;

    /**
     * 羣組名稱
     */
    private String groupName;

    /**
     * 羣組頭像 URL
     */
    private String groupAvatarUrl;


    /**
     * BOT ID
     */
    private Long selfId;

    /**
     * 最後更新時間
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime lastUpdated;

}