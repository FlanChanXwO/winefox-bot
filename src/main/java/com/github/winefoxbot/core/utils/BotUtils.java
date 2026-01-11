package com.github.winefoxbot.core.utils;

import com.github.winefoxbot.core.exception.common.BusinessException;
import com.github.winefoxbot.core.model.dto.BroadcastMessageResult;
import com.github.winefoxbot.core.model.dto.GroupMemberInfo;
import com.github.winefoxbot.core.model.dto.SendMsgResult;
import com.github.winefoxbot.core.model.enums.GroupMemberRole;
import com.github.winefoxbot.core.model.enums.MessageType;
import com.mikuac.shiro.core.Bot;
import com.mikuac.shiro.core.BotContainer;
import com.mikuac.shiro.dto.action.common.ActionData;
import com.mikuac.shiro.dto.action.common.ActionRaw;
import com.mikuac.shiro.dto.action.common.MsgId;
import com.mikuac.shiro.dto.action.response.GroupFilesResp;
import com.mikuac.shiro.dto.action.response.GroupInfoResp;
import com.mikuac.shiro.dto.action.response.GroupMemberInfoResp;
import com.mikuac.shiro.dto.action.response.StrangerInfoResp;
import com.mikuac.shiro.dto.event.Event;
import com.mikuac.shiro.dto.event.message.AnyMessageEvent;
import com.mikuac.shiro.dto.event.message.GroupMessageEvent;
import com.mikuac.shiro.dto.event.message.MessageEvent;
import com.mikuac.shiro.dto.event.message.PrivateMessageEvent;
import com.mikuac.shiro.dto.event.notice.PokeNoticeEvent;
import lombok.extern.slf4j.Slf4j;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * @author FlanChan (badapple495@outlook.com)
 * @since 2025-12-08-21:02
 */
@Slf4j
public final class BotUtils {
    private BotUtils() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }

    /**
     * 获取群成员昵称
     *
     * @param bot
     * @param groupId
     * @param userId
     * @return
     */
    public static String getGroupMemberNickname(Bot bot, Long groupId, Long userId) {
        // 如果是陌生人，使用陌生人信息接口获取昵称
        if (groupId == null) {
            return getUserNickname(bot, userId);
        } else {
            // 否则使用群成员信息接口获取昵称
            ActionData<GroupMemberInfoResp> groupMemberInfo = bot.getGroupMemberInfo(groupId, userId, false);
            if (groupMemberInfo != null && groupMemberInfo.getRetCode() == 0) {
                String card = groupMemberInfo.getData().getCard();
                return card.isBlank() ? groupMemberInfo.getData().getNickname() : card;
            } else {
                return getUserNickname(bot, userId);
            }
        }
    }


    /**
     * 获取陌生人昵称
     *
     * @param bot
     * @param userId
     * @return
     */
    public static String getStrangeNickname(Bot bot, Long userId) {
        return getUserNickname(bot, userId);
    }


    /**
     * 获取群成员昵称
     *
     * @param bot
     * @param groupId
     * @param userId
     * @return
     */
    public static GroupMemberInfo getGroupMemberInfo(Bot bot, Long groupId, Long userId) {
        GroupMemberInfo groupMember = new GroupMemberInfo();
        groupMember.setUserId(userId);
        groupMember.setGroupId(groupId);

        ActionData<GroupMemberInfoResp> groupMemberInfo = bot.getGroupMemberInfo(groupId, userId, false);
        if (groupMemberInfo != null && groupMemberInfo.getRetCode() == 0){
            GroupMemberInfoResp data = groupMemberInfo.getData();
            groupMember.setNickname(data.getNickname());
            groupMember.setCard(data.getCard());
            groupMember.setRole(GroupMemberRole.fromValue(data.getRole()));
        } else {
            ActionData<StrangerInfoResp> strangerInfo = bot.getStrangerInfo(userId, false);
            if (strangerInfo != null && strangerInfo.getRetCode() == 0) {
                StrangerInfoResp data = strangerInfo.getData();
                groupMember.setNickname(data.getNickname());
                groupMember.setCard(data.getNickname());
            } else {
                groupMember.setNickname(userId.toString());
                groupMember.setCard(userId.toString());
            }
        }
        return groupMember;
    }

    public static String getGroupName(Bot bot, Long groupId) {
        ActionData<GroupInfoResp> resp = bot.getGroupInfo(groupId, true);
        return  resp.getRetCode() == 0 ? resp.getData().getGroupName() : groupId.toString();
    }


    public static String getUserNickname(Bot bot, Long userId) {
        ActionData<StrangerInfoResp> resp = bot.getStrangerInfo(userId, true);
        if (resp != null &&  resp.getRetCode() == 0) {
            return resp.getData().getNickname();
        }
        return userId.toString();
    }

    public static boolean isAdmin(Bot bot, Long groupId) {
        ActionData<GroupMemberInfoResp> groupMemberInfoResp = bot.getGroupMemberInfo(groupId, bot.getSelfId(), true);
        if (groupMemberInfoResp != null && groupMemberInfoResp.getRetCode() == 0) {
            String role = groupMemberInfoResp.getData().getRole();
            return "admin".equals(role);
        }
        return false;
    }

    public static Long getSessionId(AnyMessageEvent event) {
        return switch (MessageType.fromValue(event.getMessageType())) {
            case PRIVATE -> event.getUserId();
            case GROUP -> event.getGroupId();
        };
    }

    public static Long getSessionId(GroupMessageEvent event) {
        return event.getGroupId();
    }

    public static Long getSessionId(PrivateMessageEvent event) {
        return event.getUserId();
    }


    public static String getSessionIdWithPrefix(AnyMessageEvent event) {
        Long groupId = event.getGroupId();
        Long userId = event.getUserId();
        switch (MessageType.fromValue(event.getGroupId() != null ? "group" : "private")) {
            case GROUP -> {
                // 群聊场景的 Key
                return "group_" + groupId + "_" + userId;
            }
            case PRIVATE -> {
                // 私聊场景的 Key
                return "private_" + userId;
            }
            default -> throw new IllegalArgumentException("Unsupported session type: " + event.getSubType());
        }
    }

    public static MessageType checkStrictSessionIdType(String sessionIdWithPrefix) {
        if (sessionIdWithPrefix == null || sessionIdWithPrefix.isEmpty()) {
            throw new IllegalArgumentException("Session ID cannot be null or empty");
        }
        if (sessionIdWithPrefix.startsWith("group_")) {
            return MessageType.GROUP;
        } else if (sessionIdWithPrefix.startsWith("private_")) {
            return MessageType.PRIVATE;
        } else {
            throw new IllegalArgumentException("Invalid Session ID format: " + sessionIdWithPrefix);
        }
    }

    public static Long removeSessionIdPrefix(String sessionIdWithPrefix) {
        MessageType type = checkStrictSessionIdType(sessionIdWithPrefix);
        switch (type) {
            case GROUP -> {
                String[] parts = sessionIdWithPrefix.split("_");
                if (parts.length >= 3) {
                    return Long.parseLong(parts[1]);
                } else {
                    throw new IllegalArgumentException("Invalid group session ID format: " + sessionIdWithPrefix);
                }
            }
            case PRIVATE -> {
                String[] parts = sessionIdWithPrefix.split("_");
                if (parts.length >= 2) {
                    return Long.parseLong(parts[1]);
                } else {
                    throw new IllegalArgumentException("Invalid private session ID format: " + sessionIdWithPrefix);
                }
            }
            default -> throw new IllegalArgumentException("Unsupported session type: " + type);
        }
    }


}