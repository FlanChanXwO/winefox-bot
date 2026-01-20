package com.github.winefoxbot.core.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.mikuac.shiro.common.utils.ShiroUtils;
import com.mikuac.shiro.dto.action.response.GroupInfoResp;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Shiro 羣組實體
 */
@Data
@TableName("shiro_groups")
public class ShiroGroup {

    /**
     * 群组 ID
     */
    @TableId(value = "group_id", type = IdType.INPUT)
    private Long groupId;

    /**
     * 群组名稱
     */
    private String groupName;

    /**
     * 群组頭像 URL
     */
    private String groupAvatarUrl;
    /**
     * 群成員數
     */
    private Integer memberCount;
    /**
     * 最大群成员数
     */
    private Integer maxMemberCount;

    /**
     * 群权限
     */
    private Integer groupLevel;
    /**
     * 是否啟用功能
     */
    @TableField(insertStrategy = FieldStrategy.NOT_NULL, updateStrategy = FieldStrategy.NOT_NULL)
    private Boolean enabled;
    /**
     * BOT ID
     */
    private Long selfId;

    /**
     * 最後更新時間
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime lastUpdated;


    public static ShiroGroup convertToShiroGroup(GroupInfoResp groupInfoResp, Long selfId) {
        ShiroGroup shiroGroup = new ShiroGroup();
        shiroGroup.setGroupId(groupInfoResp.getGroupId());
        shiroGroup.setGroupName(groupInfoResp.getGroupName());
        shiroGroup.setSelfId(selfId);
        shiroGroup.setGroupLevel(groupInfoResp.getGroupLevel() != null ? groupInfoResp.getGroupLevel() : 0);
        shiroGroup.setMemberCount(groupInfoResp.getMemberCount());
        shiroGroup.setMaxMemberCount(groupInfoResp.getMaxMemberCount());
        shiroGroup.setGroupAvatarUrl(ShiroUtils.getGroupAvatar(shiroGroup.getGroupId(), 0));
        return shiroGroup;
    }

}