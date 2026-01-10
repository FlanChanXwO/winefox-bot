package com.github.winefoxbot.core.utils;

import com.github.winefoxbot.core.model.dto.BroadcastMessageResult;
import com.github.winefoxbot.core.model.dto.SendMsgResult;
import com.github.winefoxbot.core.model.enums.MessageType;
import com.mikuac.shiro.core.Bot;
import com.mikuac.shiro.core.BotContainer;
import com.mikuac.shiro.dto.action.common.ActionData;
import com.mikuac.shiro.dto.action.common.MsgId;
import com.mikuac.shiro.dto.event.Event;
import com.mikuac.shiro.dto.event.message.AnyMessageEvent;
import com.mikuac.shiro.dto.event.message.GroupMessageEvent;
import com.mikuac.shiro.dto.event.message.PrivateMessageEvent;
import com.mikuac.shiro.dto.event.notice.PokeNoticeEvent;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * @author FlanChan (badapple495@outlook.com)
 * @since 2026-01-07-23:45
 */
@Slf4j
public final class SendMsgUtil {

    private SendMsgUtil() {
        throw new UnsupportedOperationException("工具类不允许实例化");
    }

    public static SendMsgResult sendMsgByBotContainer(BotContainer botContainer, Long targetId, String message, boolean escape, MessageType messageType) {
        Bot bot = getFirstBot(botContainer);
        ActionData<MsgId> response;
        if (messageType == MessageType.GROUP) {
            response = bot.sendGroupMsg(targetId, message, escape);
        } else if (messageType == MessageType.PRIVATE) {
            response = bot.sendPrivateMsg(targetId, message, escape);
        } else {
            throw new IllegalArgumentException("Unsupported message type: " + messageType);
        }
        return buildResult(response);
    }

    public static List<Map<Long, BroadcastMessageResult>> broadcastSendGroupMsgByContainer(BotContainer botContainer, List<Long> groupIds, String message, boolean escape) {
        validateBotContainer(botContainer);
        return botContainer.robots.values().stream()
                .map(bot -> Map.of(bot.getSelfId(), broadcastSendGroupMsg(bot, groupIds, message, escape)))
                .collect(Collectors.toList());
    }

    public static BroadcastMessageResult broadcastSendGroupMsg(Bot bot, List<Long> groupIds, String message, boolean escape) {
        return broadcast(groupIds, groupId -> bot.sendGroupMsg(groupId, message, escape));
    }

    public static BroadcastMessageResult broadcastSendPrivateMsg(Bot bot, List<Long> userIds, String message, boolean escape) {
        return broadcast(userIds, userId -> bot.sendPrivateMsg(userId, message, escape));
    }

    public static List<Map<Long, BroadcastMessageResult>> broadcastSendPrivateMsgByContainer(BotContainer botContainer, List<Long> userIds, String message, boolean escape) {
        validateBotContainer(botContainer);
        return botContainer.robots.values().stream()
                .map(bot -> Map.of(bot.getSelfId(), broadcastSendPrivateMsg(bot, userIds, message, escape)))
                .collect(Collectors.toList());
    }

    public static SendMsgResult sendMsgByEvent(Bot bot, Event event, String message, boolean escape) {
        return switch (event) {
            case AnyMessageEvent e -> buildResult(bot.sendMsg(e, message, escape));
            case GroupMessageEvent e -> buildResult(bot.sendGroupMsg(e.getGroupId(), message, escape));
            case PrivateMessageEvent e -> buildResult(bot.sendPrivateMsg(e.getUserId(), message, escape));
            case PokeNoticeEvent e -> {
                if (e.getGroupId() != null) {
                    yield buildResult(bot.sendGroupMsg(e.getGroupId(), message, escape));
                }
                yield buildResult(bot.sendPrivateMsg(e.getTargetId(), message, escape));
            }
            default -> new SendMsgResult(false, "消息发送事件类型不支持");
        };
    }

    public static SendMsgResult sendPrivateMsg(Bot bot, Long userId, String message, boolean escape) {
        return buildResult(bot.sendPrivateMsg(userId, message, escape));
    }

    public static SendMsgResult sendGroupMsg(Bot bot, Long groupId, String message, boolean escape) {
        return buildResult(bot.sendGroupMsg(groupId, message, escape));
    }

    public static CompletableFuture<SendMsgResult> sendMsgByEventAsync(Bot bot, Event event, String message, boolean escape) {
        return supplyAsyncResult(() -> sendMsgByEvent(bot, event, message, escape));
    }

    public static CompletableFuture<SendMsgResult> sendPrivateMsgAsync(Bot bot, Long userId, String message, boolean escape) {
        return supplyAsyncResult(() -> sendPrivateMsg(bot, userId, message, escape));
    }

    public static CompletableFuture<SendMsgResult> sendGroupIdMsgAsync(Bot bot, Long groupId, String message, boolean escape) {
        return supplyAsyncResult(() -> sendGroupMsg(bot, groupId, message, escape));
    }

    // --- Private Helper Methods ---

    private static Bot getFirstBot(BotContainer botContainer) {
        validateBotContainer(botContainer);
        return botContainer.robots.values().iterator().next();
    }

    private static void validateBotContainer(BotContainer botContainer) {
        if (botContainer == null || botContainer.robots.isEmpty()) {
            throw new IllegalArgumentException("BotContainer is null or has no bots available.");
        }
    }

    private static SendMsgResult buildResult(ActionData<MsgId> response) {
        if (response != null && response.getRetCode() == 0) {
            return new SendMsgResult(true, "Message sent successfully", response.getData().getMessageId());
        }
        String status = response != null ? String.valueOf(response.getStatus()) : "Unknown Error";
        return new SendMsgResult(false, "Failed to send message: " + status);
    }

    private static BroadcastMessageResult broadcast(List<Long> ids, Function<Long, ActionData<MsgId>> action) {
        List<Long> successList = new ArrayList<>();
        List<Long> failedList = new ArrayList<>();

        for (Long id : ids) {
            try {
                ActionData<MsgId> response = action.apply(id);
                if (response != null && response.getRetCode() == 0) {
                    successList.add(id);
                } else {
                    failedList.add(id);
                }
            } catch (Exception e) {
                log.error("Failed to send broadcast message to id: {}", id, e);
                failedList.add(id);
            }
        }
        return new BroadcastMessageResult(successList, failedList, failedList.isEmpty());
    }

    private static CompletableFuture<SendMsgResult> supplyAsyncResult(Supplier<SendMsgResult> supplier) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return supplier.get();
            } catch (Exception e) {
                log.error("Async message sending failed", e);
                return new SendMsgResult(false, "Failed to send message: " + e.getMessage());
            }
        });
    }
}