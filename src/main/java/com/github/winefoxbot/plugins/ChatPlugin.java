package com.github.winefoxbot.plugins;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.winefoxbot.annotation.Block;
import com.github.winefoxbot.annotation.Limit;
import com.github.winefoxbot.annotation.PluginFunction;
import com.github.winefoxbot.config.WineFoxBotConfig;
import com.github.winefoxbot.model.enums.Permission;
import com.github.winefoxbot.service.chat.DeepSeekService;
import com.github.winefoxbot.service.shiro.ShiroMessagesService;
import com.github.winefoxbot.utils.BotUtils;
import com.mikuac.shiro.annotation.*;
import com.mikuac.shiro.annotation.common.Order;
import com.mikuac.shiro.annotation.common.Shiro;
import com.mikuac.shiro.common.utils.MsgUtils;
import com.mikuac.shiro.core.Bot;
import com.mikuac.shiro.dto.event.message.AnyMessageEvent;
import com.mikuac.shiro.dto.event.message.GroupMessageEvent;
import com.mikuac.shiro.dto.event.message.PrivateMessageEvent;
import com.mikuac.shiro.dto.event.notice.PokeNoticeEvent;
import com.mikuac.shiro.enums.AtEnum;
import com.mikuac.shiro.enums.MsgTypeEnum;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Shiro
@ConditionalOnClass(DeepSeekService.class)
@Component
@Slf4j
@RequiredArgsConstructor
public class ChatPlugin {

    private final DeepSeekService deepSeekService;
    private final ShiroMessagesService shiroMessagesService;
    private final WineFoxBotConfig wineFoxBotConfig;
    private final ObjectMapper objectMapper;


    @PluginFunction(group = "聊天功能",
            name = "清空会话",
            description = "清空当前会话的消息记录，重新开始对话。",
            permission = Permission.ADMIN,
            commands = {"/清空会话"})
    @AnyMessageHandler
    @MessageHandlerFilter(types = MsgTypeEnum.text, cmd = "^/清空会话$")
    public void clearConversation(Bot bot, AnyMessageEvent event) {
        Long groupId = event.getGroupId();
        Long sessionId = (groupId != null) ? groupId : event.getUserId();
        String sessionType = (groupId != null) ? "group" : "private";

        shiroMessagesService.clearConversation(sessionId, sessionType);
        bot.sendMsg(event, "当前会话的消息记录已经被酒狐忘掉啦，可以开始新的聊天咯！", false);
    }



    @PrivateMessageHandler
    @Async
    @Order(100)
    @Block
    public void handlePrivateChatMessage(Bot bot, PrivateMessageEvent event) {
        String plainMessage = BotUtils.getPlainTextMessage(event.getMessage());
        String[] prefixes = {"/", "", "$"};
        if (plainMessage.isEmpty()) {
            return;
        }
        for (String prefix : prefixes) {
            if (plainMessage.startsWith(prefix)) {
                return;
            }
        }



        Long userId = event.getUserId();
        Long sessionId = userId;
        String sessionType = "private";

        // **FIXED**: Construct the userMsg object for the current message.
        // This is crucial for the AI to know what message to respond to.
        ObjectNode userMsg = objectMapper.createObjectNode();
        String nickname = BotUtils.getUserNickname(bot, userId);

        userMsg.put("sender", "user");
        userMsg.put("uid", String.valueOf(userId));
        userMsg.put("nickname", nickname);
        userMsg.put("message", plainMessage);
        userMsg.put("isMaster", wineFoxBotConfig.getSuperusers().contains(userId));

        // The AOP interceptor saves the message, but we pass the constructed object
        // to the AI service for immediate context.
        String resp = deepSeekService.complete(sessionId, sessionType, userMsg);

        // Build and send the reply.
        if (resp != null && !resp.isEmpty()) {
            MsgUtils msgBuilder = MsgUtils.builder();
            msgBuilder.text(resp);
            bot.sendPrivateMsg(userId, msgBuilder.build(), false);
        }
    }


    @PluginFunction(group = "聊天功能",
            name = "聊天回复",
            description = "当用户在群聊中At机器人发送消息时，进行智能回复。",
            permission = Permission.USER
    )
    @GroupMessageHandler
    @MessageHandlerFilter(at = AtEnum.NEED)
    @Async
    @Order(100)
    @Block
    @Limit(userPermits = 1, timeInSeconds = 10, notificationIntervalSeconds = 30, message = "说话太快了，酒狐需要思考一会儿哦~")
    public void handleGroupChatMessage(Bot bot, GroupMessageEvent event) {
        String plainMessage = BotUtils.getPlainTextMessage(event.getMessage());
        if (plainMessage.isEmpty() || plainMessage.startsWith("/")) {
            return;
        }

        Long groupId = event.getGroupId();
        Long userId = event.getUserId();
        Long sessionId = groupId;
        String sessionType = "group";

        ObjectNode userMsg = objectMapper.createObjectNode();
        String nickname = BotUtils.getGroupMemberNickname(bot, groupId, userId);

        userMsg.put("sender", "user");
        userMsg.put("uid", String.valueOf(userId));
        userMsg.put("nickname", nickname);
        userMsg.put("message", plainMessage);

        String resp = deepSeekService.complete(sessionId, sessionType, userMsg);

        // Build and send the reply.
        if (resp != null && !resp.isEmpty()) {
            MsgUtils msgBuilder = MsgUtils.builder();
            msgBuilder.at(userId).text(" "); // Add a space after the @
            msgBuilder.text(resp);
            bot.sendGroupMsg(groupId, msgBuilder.build(), false);
        }
    }

    @PluginFunction(
            group = "聊天功能",
            name = "群聊戳一戳回复",
            description = "当用户戳一戳机器人时，进行智能回复。",
            permission = Permission.USER
    )
    @GroupPokeNoticeHandler
    @Limit(userPermits = 1, timeInSeconds = 10, notificationIntervalSeconds = 30, message = "戳得太快了，酒狐需要休息一下哦~")
    @Async
    public void handleGroupPokeNotice(Bot bot, PokeNoticeEvent event) {
        deepSeekService.handlePokeMessage(bot, event, event.getGroupId() != null);
    }


    @PrivatePokeNoticeHandler
    @Limit(userPermits = 1, timeInSeconds = 1, notificationIntervalSeconds = 30, message = "戳得太快了，酒狐需要休息一下哦~")
    @Async
    public void handlePrivatePokeNotice(Bot bot, PokeNoticeEvent event) {
        deepSeekService.handlePokeMessage(bot, event, event.getGroupId() != null);
    }
}


