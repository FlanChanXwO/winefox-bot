package com.github.winefoxbot.aop.handler;

import cn.hutool.core.util.RandomUtil;
import cn.hutool.json.JSONUtil;
import com.github.winefoxbot.model.entity.ShiroMessage;
import com.github.winefoxbot.service.shiro.ShiroMessagesService;
import com.github.winefoxbot.utils.BotUtils;
import com.mikuac.shiro.core.Bot;
import com.mikuac.shiro.dto.action.common.ActionData;
import com.mikuac.shiro.dto.action.common.MsgId;
import com.mikuac.shiro.dto.event.message.AnyMessageEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class BotSendMsgHandler {

    private final ShiroMessagesService shiroMessagesService;

    @Async
    public void handleAnyMessageEvent(Bot bot, Object[] methodArgs, Object result) {
        if (methodArgs == null || methodArgs.length < 3) {
            log.warn("sendMsg method called with unexpected number of arguments: {}", methodArgs.length);
            return;
        }

        try {
            AnyMessageEvent event = (AnyMessageEvent) methodArgs[0];
            String messageContent = (String) methodArgs[1];
            String messageType = event.getMessageType();
            String id = event.getGroupId() == null ? String.valueOf(event.getUserId()) : String.valueOf(event.getGroupId());
            Integer messageId = event.getMessageId();
            ShiroMessage message = new ShiroMessage();
            message.setMessageId(Long.valueOf(messageId));
            message.setSelfId(bot.getSelfId());
            message.setMessageType(messageType);
            message.setDirection("message_sent");
            message.setUserId(bot.getSelfId());
            if ("private".equals(messageType)) {
                message.setUserId(Long.valueOf(id));
            } else if ("group".equals(messageType)) {
                message.setGroupId(Long.valueOf(id));
            }
            message.setUserId(bot.getSelfId());
            if (messageContent instanceof String msg) {
                message.setMessage(JSONUtil.parseArray(BotUtils.parseCQtoJsonStr(messageContent,true)));
                message.setPlainText(BotUtils.getPlainTextMessage(msg));
            } else {
                message.setMessage(JSONUtil.parseArray(messageContent));
                message.setPlainText("");
            }

            shiroMessagesService.save(message);

        } catch (Exception e) {
            log.error("Error handling sent message", e);
        }
    }

    @Async
    public void handleGroupMessageEvent(Bot bot, Object[] methodArgs, Object result) {
        if (methodArgs == null || methodArgs.length < 3) {
            log.warn("sendMsg method called with unexpected number of arguments: {}", methodArgs.length);
            return;
        }

        try {
            long groupId = (long) methodArgs[0];
            ActionData<MsgId> res = (ActionData<MsgId>) result;
            String messageContent = (String) methodArgs[1];
            String messageType = "group";
            String id = String.valueOf(groupId);
            Integer messageId = res.getData().getMessageId();
            ShiroMessage message = new ShiroMessage();
            message.setMessageId(Long.valueOf(messageId));
            message.setSelfId(bot.getSelfId());
            message.setMessageType(messageType);
            message.setDirection("message_sent");
            message.setGroupId(Long.valueOf(id));
            message.setUserId(bot.getSelfId());
            log.info("instance of messageContent: {}", messageContent.getClass().getName());
            if (messageContent instanceof String msg) {
                message.setMessage(JSONUtil.parseArray(BotUtils.parseCQtoJsonStr(messageContent,true)));
                message.setPlainText(BotUtils.getPlainTextMessage(msg));
            } else {
                message.setMessage(JSONUtil.parseArray(messageContent));
                message.setPlainText("");
            }

            shiroMessagesService.save(message);

        } catch (Exception e) {
            log.error("Error handling sent message", e);
        }
    }

    @Async
    public void handlePrivateMessageEvent(Bot bot, Object[] methodArgs, Object result) {
        if (methodArgs == null || methodArgs.length < 3) {
            log.warn("sendMsg method called with unexpected number of arguments: {}", methodArgs.length);
            return;
        }

        try {
            ActionData<MsgId> res = (ActionData<MsgId>) result;
            Object messageContent = methodArgs[1];
            String messageType = "private";
            MsgId data = res.getData();
            Integer messageId = data != null ? data.getMessageId() : RandomUtil.randomInt();
            ShiroMessage message = new ShiroMessage();
            message.setMessageId(Long.valueOf(messageId));
            message.setSelfId(bot.getSelfId());
            message.setMessageType(messageType);
            message.setDirection("message_sent");
            message.setUserId(bot.getSelfId());
            log.info("instance of messageContent: {}", messageContent.getClass().getName());
            log.info("messageContent: {}", messageContent);
            if (messageContent instanceof String msg) {
                message.setMessage(JSONUtil.parseArray(BotUtils.parseCQtoJsonStr(msg,true)));
                message.setPlainText(BotUtils.getPlainTextMessage(msg));
            } else {
                message.setMessage(JSONUtil.parseArray(messageContent));
                message.setPlainText("");
            }
            shiroMessagesService.save(message);
        } catch (Exception e) {
            log.error("Error handling sent message", e);
        }
    }
}