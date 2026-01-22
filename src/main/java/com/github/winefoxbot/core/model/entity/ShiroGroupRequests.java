package com.github.winefoxbot.core.model.entity;

import cn.hutool.core.date.LocalDateTimeUtil;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.github.winefoxbot.core.context.BotContext;
import com.github.winefoxbot.core.model.enums.common.GroupAddRequestType;
import com.github.winefoxbot.core.model.enums.common.RequestStatus;
import com.github.winefoxbot.core.model.type.GenericEnumTypeHandler;
import com.mikuac.shiro.common.utils.ShiroUtils;
import com.mikuac.shiro.core.Bot;
import com.mikuac.shiro.dto.action.common.ActionData;
import com.mikuac.shiro.dto.action.response.GroupInfoResp;
import com.mikuac.shiro.dto.action.response.StrangerInfoResp;
import com.mikuac.shiro.dto.event.request.GroupAddRequestEvent;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * @TableName shiro_group_requests
 */
@TableName(value ="shiro_group_requests")
@Data
public class ShiroGroupRequests implements Serializable {
    @TableId(type = IdType.AUTO)
    private Integer id;

    private String flag;

    @TableField(typeHandler = GenericEnumTypeHandler.class)
    private GroupAddRequestType subType;

    private Long groupId;

    private Long userId;

    private Long botId;

    private String comment;

    private String groupName;

    private String groupAvatarUrl;

    private String nickname;
    @TableField(typeHandler = GenericEnumTypeHandler.class)
    private RequestStatus status;

    private LocalDateTime handledAt;

    private LocalDateTime receivedAt;

    @TableField(exist = false)
    private static final long serialVersionUID = 1L;

    public static ShiroGroupRequests convertToEntity(GroupAddRequestEvent event) {
        ShiroGroupRequests entity = new ShiroGroupRequests();
        entity.setFlag(event.getFlag());
        entity.setSubType(GroupAddRequestType.fromValue(event.getSubType()));
        entity.setGroupId(event.getGroupId());
        entity.setUserId(entity.getSubType() == GroupAddRequestType.ADD ? event.getUserId() : event.getInvitorId());
        entity.setComment(event.getComment());
        entity.setBotId(event.getSelfId());
        Bot bot = BotContext.CURRENT_BOT.get();
        Optional<ActionData<GroupInfoResp>> groupInfoOpt = Optional.ofNullable(bot.getGroupInfo(entity.getGroupId(), false));
        if (groupInfoOpt.isPresent() && groupInfoOpt.get().getRetCode() == 0) {
            entity.setGroupName(groupInfoOpt.get().getData().getGroupName());
        } else {
            entity.setGroupName("未知群组");
        }
        Optional<ActionData<StrangerInfoResp>> strangerInfoOpt = Optional.ofNullable(bot.getStrangerInfo(event.getUserId(), false));
        if (strangerInfoOpt.isPresent() && strangerInfoOpt.get().getRetCode() == 0) {
            entity.setNickname(strangerInfoOpt.get().getData().getNickname());
        } else {
            entity.setNickname("未知用户");
        }
        entity.setGroupAvatarUrl(ShiroUtils.getGroupAvatar(entity.getUserId(),0));
        entity.setStatus(RequestStatus.PENDING);
        entity.setReceivedAt(LocalDateTimeUtil.of(event.getTime()));
        return entity;
    }
}