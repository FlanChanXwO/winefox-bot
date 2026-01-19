package com.github.winefoxbot.core.model.entity;

import com.baomidou.mybatisplus.annotation.*;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Date;

import com.mikuac.shiro.dto.action.response.FriendInfoResp;
import lombok.Data;

/**
 * @TableName shiro_friends
 */
@TableName(value ="shiro_friends")
@Data
public class ShiroFriends implements Serializable {
    @TableId(type = IdType.INPUT)
    private Long botId;

    private Long friendId;

    private String nickname;

    private Boolean enabled;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime lastUpdated;

    @TableField(exist = false)
    private static final long serialVersionUID = 1L;

    public static ShiroFriends convertToShiroFriend(FriendInfoResp friendInfoResp, Long selfId) {
        ShiroFriends shiroFriends = new ShiroFriends();
        shiroFriends.setBotId(selfId);
        shiroFriends.setFriendId(friendInfoResp.getUserId());
        shiroFriends.setNickname(friendInfoResp.getNickname());
        shiroFriends.setLastUpdated(LocalDateTime.now());
        return shiroFriends;
    }
}