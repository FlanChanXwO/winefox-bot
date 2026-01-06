package com.github.winefoxbot.core.aop.handler;

import cn.hutool.core.util.RandomUtil;
import cn.hutool.json.JSONUtil;
import com.github.winefoxbot.core.model.entity.ShiroMessage;
import com.github.winefoxbot.core.model.enums.MessageDirection;
import com.github.winefoxbot.core.model.enums.MessageType;
import com.github.winefoxbot.core.service.shiro.ShiroMessagesService;
import com.github.winefoxbot.core.utils.MessageConverter;
import com.mikuac.shiro.core.Bot;
import com.mikuac.shiro.dto.action.common.ActionData;
import com.mikuac.shiro.dto.action.common.MsgId;
import com.mikuac.shiro.dto.event.message.AnyMessageEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * @author FlanChan
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class BotSendMsgHandler {

    private final ShiroMessagesService shiroMessagesService;

    @Async
    public void handleAnyMessageEvent(Bot bot, Object[] methodArgs, Object result) {
        if (!isValidArgs(methodArgs, 2)) return; // 简化参数校验

        try {
            AnyMessageEvent event = (AnyMessageEvent) methodArgs[0];
            String messageContent = (String) methodArgs[1];

            createAndSaveMessage(
                    bot.getSelfId(),
                    event.getMessageId(), // 使用接收到的消息ID作为记录ID
                    MessageType.fromValue(event.getMessageType()),
                    messageContent,
                    // 会话ID：群聊用群号，私聊用用户QQ号
                    Optional.ofNullable(event.getGroupId()).orElse(event.getUserId())
            );
        } catch (Exception e) {
            log.error("Error handling sent message from AnyMessageEvent", e);
        }
    }

    @Async
    public void handleGroupMessageEvent(Bot bot, Object[] methodArgs, Object result) {
        if (!isValidArgs(methodArgs, 2)) return;

        try {
            long groupId = (long) methodArgs[0];
            String messageContent = (String) methodArgs[1];
            long messageId = getMessageIdFromResult(result);

            createAndSaveMessage(
                    bot.getSelfId(),
                    messageId,
                    MessageType.GROUP,
                    messageContent,
                    groupId
            );
        } catch (Exception e) {
            log.error("Error handling sent group message", e);
        }
    }

    @Async
    public void handlePrivateMessageEvent(Bot bot, Object[] methodArgs, Object result) {
        if (!isValidArgs(methodArgs, 2)) return;

        try {
            // 私聊的第一个参数是 userId，可以用来做 sessionId
            long userId = (long) methodArgs[0];
            String messageContent = (String) methodArgs[1];
            long messageId = getMessageIdFromResult(result);

            createAndSaveMessage(
                    bot.getSelfId(),
                    messageId,
                    MessageType.PRIVATE,
                    messageContent,
                    userId // 私聊的 SessionId 就是对方的 UserId
            );
        } catch (Exception e) {
            log.error("Error handling sent private message", e);
        }
    }


    /**
     * 核心逻辑：创建并保存 ShiroMessage 对象
     * @param selfId 机器人自己的ID
     * @param messageId 消息ID
     * @param messageType 消息类型 (GROUP/PRIVATE)
     * @param messageContent 消息内容 (CQ码字符串)
     * @param sessionId 会话ID (群号或用户QQ号)
     */
    private void createAndSaveMessage(long selfId, long messageId, MessageType messageType, String messageContent, long sessionId) {
        // 处理非法 sessionId 的情况
        if (sessionId < 0 || messageId < 0) {
            log.warn("Invalid sessionId or messageId. sessionId: {}, messageId: {}", sessionId, messageId);
            return;
        }

        ShiroMessage message = new ShiroMessage();
        message.setSelfId(selfId);
        message.setMessageId(messageId);
        message.setMessageType(messageType);
        message.setSessionId(sessionId);

        // 设置通用属性
        message.setDirection(MessageDirection.MESSAGE_SENT);
        message.setUserId(selfId); // 发送的消息，发送者是机器人自己

        // 解析消息内容
        if (messageContent != null) {
            message.setMessage(MessageConverter.parseCQToJSONArray(messageContent));
            message.setPlainText(MessageConverter.getPlainTextMessage(messageContent));
        } else {
            // 如果内容为空，提供默认值防止空指针
            message.setMessage(JSONUtil.createArray());
            message.setPlainText("");
        }

        shiroMessagesService.save(message);
        log.debug("Saved sent message [{}] to session [{}].", messageId, sessionId);
    }

    /**
     * 从方法执行结果中安全地提取消息ID
     * @param result Bot API调用的返回结果
     * @return 消息ID，如果获取失败则返回一个随机整数作为备用
     */
    private long getMessageIdFromResult(Object result) {
        if (result instanceof ActionData<?> actionData && actionData.getData() instanceof MsgId msgId) {
            return Optional.ofNullable(msgId.getMessageId())
                    .map(Long::valueOf)
                    .orElseGet(this::generateRandomId);
        }
        log.warn("Failed to get messageId from result, generating a random one.");
        return generateRandomId();
    }

    /**
     * 生成一个随机的 messageId 作为备用
     */
    private long generateRandomId() {
        return RandomUtil.randomInt();
    }

    /**
     * 校验方法参数数组是否有效
     * @param args 方法参数
     * @param expectedLength 期望的最小长度
     * @return 是否有效
     */
    private boolean isValidArgs(Object[] args, int expectedLength) {
        if (args == null || args.length < expectedLength) {
            log.warn("Method called with unexpected number of arguments: {}", args != null ? args.length : "null");
            return false;
        }
        return true;
    }
}
