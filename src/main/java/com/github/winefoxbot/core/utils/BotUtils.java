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
            if (groupMemberInfo.getRetCode() == 0) {
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
        if (groupMemberInfo.getRetCode() == 0) {
            GroupMemberInfoResp data = groupMemberInfo.getData();
            groupMember.setNickname(data.getNickname());
            groupMember.setCard(data.getCard());
            groupMember.setRole(GroupMemberRole.fromValue(data.getRole()));
        } else {
            ActionData<StrangerInfoResp> strangerInfo = bot.getStrangerInfo(userId, false);
            if (strangerInfo.getRetCode() == 0) {
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
        return resp.getRetCode() == 0 ? resp.getData().getGroupName() : groupId.toString();
    }


    public static String getUserNickname(Bot bot, Long userId) {
        ActionData<StrangerInfoResp> resp = bot.getStrangerInfo(userId, true);
        if (resp.getRetCode() == 0) {
            return resp.getData().getNickname();
        }
        return resp.getRetCode() == 0 ? resp.getData().getNickname() : userId.toString();
    }

    public static boolean isAdmin(Bot bot, Long groupId) {
        ActionData<GroupMemberInfoResp> groupMemberInfoResp = bot.getGroupMemberInfo(groupId, bot.getSelfId(), true);
        if (groupMemberInfoResp.getRetCode() == 0) {
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


    public static SendMsgResult sendMsgByBotContainer(BotContainer botContainer, Long targetId, String message, boolean escape, MessageType messageType) {
        if (botContainer == null || botContainer.robots.isEmpty()) {
            throw new IllegalArgumentException("BotContainer is null or has no bots available.");
        }
        for (Bot bot : botContainer.robots.values()) {
            ActionData<MsgId> response;
            if (messageType == MessageType.GROUP) {
                response = bot.sendGroupMsg(targetId, message, escape);
            } else if (messageType == MessageType.PRIVATE) {
                response = bot.sendPrivateMsg(targetId, message, escape);
            } else {
                throw new IllegalArgumentException("Unsupported message type: " + messageType);
            }
            if (response.getRetCode() == 0) {
                return new SendMsgResult(true, "Message sent successfully", response.getData().getMessageId());
            } else {
                return new SendMsgResult(false, "Failed to send message: " + response.getStatus());
            }
        }
        return new SendMsgResult(false, "No bots available to send the message");
    }

    public static List<Map<Long, BroadcastMessageResult>> broadcastSendGroupMsgByContainer(BotContainer botContainer, List<Long> groupIds, String message, boolean escape) {
        if (botContainer == null || botContainer.robots.isEmpty())
            throw new IllegalArgumentException("BotContainer is null or has no bots available.");
        List<Map<Long, BroadcastMessageResult>> results = new ArrayList<>();
        for (Bot bot : botContainer.robots.values()) {
            BroadcastMessageResult result = broadcastSendGroupMsg(bot, groupIds, message, escape);
            results.add(Map.of(bot.getSelfId(), result));
        }
        return results;
    }


    public static BroadcastMessageResult broadcastSendGroupMsg(Bot bot, List<Long> groupIds, String message, boolean escape) {
        List<Long> successList = new ArrayList<>(groupIds.size());
        List<Long> failedList = new ArrayList<>(groupIds.size() / 2);

        for (Long groupId : groupIds) {
            try {
                ActionData<MsgId> response = bot.sendGroupMsg(groupId, message, escape);
                if (response.getRetCode() == 0) {
                    successList.add(groupId);
                } else {
                    failedList.add(groupId);
                }
            } catch (Exception e) {
                log.error("Failed to send message to group: {}", groupId, e);
                failedList.add(groupId);
            }
        }

        boolean allSuccess = failedList.isEmpty();
        return new BroadcastMessageResult(successList, failedList, allSuccess);
    }


    public static BroadcastMessageResult broadcastSendPrivateMsg(Bot bot, List<Long> userIds, String message, boolean escape) {
        List<Long> successList = new ArrayList<>(userIds.size());
        List<Long> failedList = new ArrayList<>(userIds.size() / 2);

        for (Long userId : userIds) {
            try {
                ActionData<MsgId> response = bot.sendPrivateMsg(userId, message, escape);
                if (response.getRetCode() == 0) {
                    successList.add(userId);
                } else {
                    failedList.add(userId);
                }
            } catch (Exception e) {
                log.error("Failed to send message to user: {}", userId, e);
                failedList.add(userId);
            }
        }
        boolean allSuccess = failedList.isEmpty();
        return new BroadcastMessageResult(successList, failedList, allSuccess);
    }


    public static List<Map<Long, BroadcastMessageResult>> broadcastSendPrivateMsgByContainer(BotContainer botContainer, List<Long> userIds, String message, boolean escape) {
        if (botContainer == null || botContainer.robots.isEmpty())
            throw new IllegalArgumentException("BotContainer is null or has no bots available.");
        List<Map<Long, BroadcastMessageResult>> results = new ArrayList<>();
        for (Bot bot : botContainer.robots.values()) {
            BroadcastMessageResult result = broadcastSendPrivateMsg(bot, userIds, message, escape);
            results.add(Map.of(bot.getSelfId(), result));
        }
        return results;
    }

    public static SendMsgResult sendMsgByEvent(Bot bot, Event event, String message, boolean escape) {
        return switch (event) {
            case AnyMessageEvent e -> handleAnyMsgSending(bot, e, message, escape);
            case GroupMessageEvent e -> handleGroupMsgSending(bot, e, message, escape);
            case PrivateMessageEvent e -> handlePrivateMsgSending(bot, e, message, escape);
            case PokeNoticeEvent e -> {
                if (e.getGroupId() == null) {
                    yield handlePrivateMsgSending(bot, new PrivateMessageEvent() {{
                        setUserId(e.getTargetId());
                    }}, message, escape);
                } else {
                    yield handleGroupMsgSending(bot, new GroupMessageEvent() {{
                        setGroupId(e.getGroupId());
                    }}, message, escape);
                }
            }
            default -> new SendMsgResult(false, "消息发送事件类型不支持");
        };
    }

    private static SendMsgResult handleAnyMsgSending(Bot bot, AnyMessageEvent event, String message, boolean escape) {
        ActionData<MsgId> response = bot.sendMsg(event, message, escape);
        if (response.getRetCode() == 0) {
            return new SendMsgResult(true, "Message sent successfully", response.getData().getMessageId());
        } else {
            return new SendMsgResult(false, "Failed to send message: " + response.getStatus());
        }
    }

    private static SendMsgResult handlePrivateMsgSending(Bot bot, PrivateMessageEvent event, String message, boolean escape) {
        ActionData<MsgId> response = bot.sendPrivateMsg(event.getUserId(), message, escape);
        if (response.getRetCode() == 0) {
            return new SendMsgResult(true, "Message sent successfully", response.getData().getMessageId());
        } else {
            return new SendMsgResult(false, "Failed to send message: " + response.getStatus());
        }
    }

    private static SendMsgResult handleGroupMsgSending(Bot bot, GroupMessageEvent event, String message, boolean escape) {
        ActionData<MsgId> response = bot.sendGroupMsg(event.getGroupId(), message, escape);
        if (response.getRetCode() == 0) {
            return new SendMsgResult(true, "Message sent successfully", response.getData().getMessageId());
        } else {
            return new SendMsgResult(false, "Failed to send message: " + response.getStatus());
        }
    }

    public static CompletableFuture<SendMsgResult> sendMsgByEventAsync(Bot bot, Event event, String message, boolean escape) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return sendMsgByEvent(bot, event, message, escape);
            } catch (Exception e) {
                log.error("Failed to send message by event", e);
                return new SendMsgResult(false, "Failed to send message: " + e.getMessage());
            }
        });
    }

    public static SendMsgResult uploadFile(Bot bot, MessageEvent event, Path filePath, String fileName) {
        if (event instanceof PrivateMessageEvent e) {
            return handlePrivateFileUpload(bot, e, filePath, fileName);
        } else if (event instanceof AnyMessageEvent e) {
            if (e.getGroupId() != null) {
                return handleGroupFileUpload(bot, e, filePath, fileName);
            } else {
                return handlePrivateFileUpload(bot, e, filePath, fileName);
            }
        } else if (event instanceof GroupMessageEvent e) {
            return handleGroupFileUpload(bot, e, filePath, fileName);
        } else {
            log.warn("Unsupported event type for file upload: {}", event.getClass().getName());
            throw new IllegalStateException("Unexpected event value: " + event);
        }
    }

    private static SendMsgResult handleGroupFileUpload(Bot bot, GroupMessageEvent event, Path filePath, String fileName) {
        log.info("Uploading file: {} as {}", filePath.toString(), fileName);
        ActionRaw actionRaw = bot.uploadGroupFile(event.getGroupId(), filePath.toAbsolutePath().toString(), fileName);
        Integer retCode = actionRaw.getRetCode();
        if (retCode != 0) {
            throw new BusinessException("无法上传群文件", null);
        }
        log.info("File uploaded: {} with retCode: {}, status: {}", fileName, retCode, actionRaw.getStatus());
        return new SendMsgResult(true, "File uploaded successfully");
    }

    private static SendMsgResult handlePrivateFileUpload(Bot bot, MessageEvent event, Path filePath, String fileName) {
        log.info("Uploading private file: {} as {}", filePath.toString(), fileName);
        Long userId = event.getUserId();
        ActionRaw actionRaw = bot.uploadPrivateFile(userId, filePath.toAbsolutePath().toString(), fileName);
        Integer retCode = actionRaw.getRetCode();
        if (retCode != 0) {
            throw new BusinessException("无法上传私聊文件", null);
        }
        log.info("File uploaded: {} with retCode: {}, status: {}", fileName, retCode, actionRaw.getStatus());
        return new SendMsgResult(true, "File uploaded successfully");
    }

    public static CompletableFuture<SendMsgResult> uploadFileAsync(Bot bot, MessageEvent event, Path filePath, String fileName) {
        return CompletableFuture.supplyAsync(() -> uploadFile(bot, event, filePath, fileName));
    }

    public static void deleteGroupFile(Bot bot, GroupMessageEvent groupMessageEvent, String fileName) {
        Long groupId = groupMessageEvent.getGroupId();
        if (groupId == null) {
            return;
        }
        ActionData<GroupFilesResp> groupRootFiles = bot.getGroupRootFiles(groupId);
        GroupFilesResp data = groupRootFiles.getData();
        for (GroupFilesResp.Files file : data.getFiles()) {
            if (file.getFileName().equals(fileName)) {
                bot.deleteGroupFile(groupId, file.getFileId(), file.getBusId());
                break;
            }
        }
    }
}