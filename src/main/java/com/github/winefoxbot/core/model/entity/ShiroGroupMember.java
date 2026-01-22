package com.github.winefoxbot.core.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.github.winefoxbot.core.model.enums.common.GroupMemberRole;
import com.github.winefoxbot.core.model.type.GenericEnumTypeHandler;
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
    @TableId(type = IdType.INPUT)
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
     * 成員角色
     */
    @TableField(value = "role", typeHandler = GenericEnumTypeHandler.class)
    private GroupMemberRole role;

    /**
     * 最後更新時間
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime lastUpdated;

}