package com.github.winefoxbot.core.model.entity;

import cn.hutool.core.date.LocalDateTimeUtil;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.github.winefoxbot.core.model.enums.common.RequestStatus;
import com.github.winefoxbot.core.model.type.GenericEnumTypeHandler;
import com.mikuac.shiro.dto.event.request.FriendAddRequestEvent;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * @author FlanChan
 * @TableName shiro_friend_requests
 */
@TableName(value ="shiro_friend_requests")
@Data
public class ShiroFriendRequests implements Serializable {
    @TableId(type = IdType.AUTO)
    private Integer id;

    private String flag;

    private Long userId;

    private Long botId;

    private String comment;

    private String nickname;

    private String avatarUrl;
    @TableField(typeHandler = GenericEnumTypeHandler.class)
    private RequestStatus status;

    private LocalDateTime handledAt;

    private LocalDateTime receivedAt;

    @TableField(exist = false)
    private static final long serialVersionUID = 1L;

    public static ShiroFriendRequests convertToEntity(FriendAddRequestEvent event) {
        ShiroFriendRequests shiroFriendRequests = new ShiroFriendRequests();
        shiroFriendRequests.setFlag(event.getFlag());
        shiroFriendRequests.setUserId(event.getUserId());
        shiroFriendRequests.setComment(event.getComment());
        shiroFriendRequests.setStatus(RequestStatus.PENDING);
        shiroFriendRequests.setBotId(event.getSelfId());
        shiroFriendRequests.setReceivedAt(LocalDateTimeUtil.of(event.getTime()));
        return shiroFriendRequests;
    }
}