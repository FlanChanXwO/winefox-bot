package com.github.winefoxbot.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Shiro 羣組成員實體
 */
@Data
@TableName("shiro_group_members")
public class ShiroGroupMember {

    /**
     * 羣組 ID (複合主鍵的一部分)
     */
    private Long groupId;

    /**
     * 使用者 ID (複合主鍵的一部分)
     */
    private Long userId;

    /**
     * 成員在羣組中的暱稱
     */
    private String memberNickname;

    /**
     * 最後更新時間
     */
    private LocalDateTime lastUpdated;

}