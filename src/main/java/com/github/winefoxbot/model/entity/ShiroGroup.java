package com.github.winefoxbot.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
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
     * 最後更新時間
     */
    private LocalDateTime lastUpdated;

}