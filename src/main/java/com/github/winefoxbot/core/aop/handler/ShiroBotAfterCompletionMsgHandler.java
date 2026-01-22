package com.github.winefoxbot.core.aop.handler;

import cn.hutool.json.JSONArray;
import com.github.winefoxbot.core.annotation.common.RedissonLock;
import com.github.winefoxbot.core.model.entity.ShiroGroup;
import com.github.winefoxbot.core.model.entity.ShiroMessage;
import com.github.winefoxbot.core.model.entity.ShiroUser;
import com.github.winefoxbot.core.model.enums.common.MessageDirection;
import com.github.winefoxbot.core.model.enums.common.MessageType;
import com.github.winefoxbot.core.service.shiro.ShiroGroupMembersService;
import com.github.winefoxbot.core.service.shiro.ShiroGroupsService;
import com.github.winefoxbot.core.service.shiro.ShiroMessagesService;
import com.github.winefoxbot.core.service.shiro.ShiroUsersService;
import com.github.winefoxbot.core.utils.BotUtils;
import com.github.winefoxbot.core.utils.MessageConverter;
import com.mikuac.shiro.common.utils.ShiroUtils;
import com.mikuac.shiro.core.Bot;
import com.mikuac.shiro.dto.action.common.ActionData;
import com.mikuac.shiro.dto.action.response.GroupInfoResp;
import com.mikuac.shiro.dto.event.message.AnyMessageEvent;
import com.mikuac.shiro.dto.event.message.GroupMessageEvent;
import com.mikuac.shiro.dto.event.message.MessageEvent;
import com.mikuac.shiro.dto.event.message.PrivateMessageEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class ShiroBotAfterCompletionMsgHandler {

    private final ShiroUsersService shiroUsersService;
    private final ShiroGroupsService shiroGroupsService;
    private final ShiroGroupMembersService shiroGroupMembersService;
    private final ShiroMessagesService shiroMessagesService;


    @Lazy
    @Autowired
    private ShiroBotAfterCompletionMsgHandler self;

    @Async
    public void handle(Bot bot, MessageEvent event) {
        saveMsg(bot, event);
    }

    private void saveMsg(Bot bot, MessageEvent event) {
        try {
            // 判断消息方向
            Long selfId = bot.getSelfId();
            Long userId = event.getUserId();
            boolean isSelf = userId != null && userId.equals(selfId);
            MessageDirection direction = isSelf ? MessageDirection.MESSAGE_SENT : MessageDirection.MESSAGE_RECEIVE;

            // 1. 保存或更新用户信息
            // 提取数据逻辑保持在主线程，IO操作通过 self 代理调用加锁方法
            ShiroUser user = extractUserFromEvent(bot, event);
            self.saveUserSafe(user);

            // 2. 构建消息实体
            ShiroMessage message = buildShiroMessage(bot, event, direction);

            // 3. 处理群组或私聊
            if (event instanceof GroupMessageEvent groupEvent) {
                // 保存群组信息
                ShiroGroup group = extractGroupFromEvent(bot, groupEvent);
                if (group != null) {
                    self.saveGroupSafe(group);
                }
                // 保存群成员信息
                self.saveGroupMemberSafe(groupEvent);
                message.setSessionId(groupEvent.getGroupId());
            } else {
                // 私聊处理
                message.setSessionId(event.getUserId());
            }

            // 处理非法 sessionId
            if (message.getSessionId() < 0 || message.getMessageId() < 0) {
                log.warn("Invalid sessionId or messageId. sessionId: {}, messageId: {}", message.getSessionId(), message.getMessageId());
                return;
            }

            // 4. 保存消息
            self.saveMessageSafe(message);

            log.debug("Saved {} message ID: {}, Session: {}", direction, message.getMessageId(), message.getSessionId());
        } catch (Exception e) {
            log.error("Error handling message event. event: {}", event, e);
        }
    }

    /**
     * 1. 锁住特定用户信息的更新
     * Key: save_user:lock:{userId}
     */
    @RedissonLock(prefix = "save_user:lock", key = "#user.userId")
    public void saveUserSafe(ShiroUser user) {
        shiroUsersService.saveOrUpdate(user);
    }

    /**
     * 2. 特定群组信息的更新
     */
    public void saveGroupSafe(ShiroGroup group) {
        shiroGroupsService.saveOrUpdate(group);
    }

    /**
     * 3. 锁住特定群成员信息的更新
     * Key: save_member:lock:{groupId}:{userId}
     */
    @RedissonLock(prefix = "save_member:lock", key = "#event.groupId + ':' + #event.userId")
    public void saveGroupMemberSafe(GroupMessageEvent event) {
        shiroGroupMembersService.saveOrUpdateGroupMemberInfo(event);
    }

    /**
     * 4. 消息的保存
     */
    public void saveMessageSafe(ShiroMessage message) {
        shiroMessagesService.save(message);
    }


    private ShiroMessage buildShiroMessage(Bot bot, MessageEvent event, MessageDirection direction) {
        ShiroMessage message = new ShiroMessage();

        // 安全提取 Message ID (JDK 21 Pattern Matching switch)
        long msgId = switch (event) {
            case AnyMessageEvent e -> e.getMessageId() != null ? e.getMessageId().longValue() : -1L;
            case GroupMessageEvent e -> e.getMessageId() != null ? e.getMessageId().longValue() : -1L;
            case PrivateMessageEvent e -> e.getMessageId() != null ? e.getMessageId().longValue() : -1L;
            case null, default -> -1L;
        };

        if (msgId < 0) {
            return null;
        }

        Long selfId = bot.getSelfId();
        Long userId = event.getUserId();
        MessageType messageType = MessageType.fromValue(event.getMessageType());
        JSONArray jsonMessage = MessageConverter.parseCQToJSONArray(event.getRawMessage());
        String jsonString = jsonMessage.toJSONString(1);
        log.info("[{}] | [{}] | {} : {}", direction.getValue(), messageType.getValue(), userId, jsonString.substring(0, Math.min(jsonString.length(), 1000)));

        message.setMessageId(msgId);
        message.setSelfId(selfId);
        message.setMessageType(messageType);
        message.setUserId(userId);
        message.setMessage(jsonMessage);
        message.setPlainText(MessageConverter.getPlainTextMessage(jsonMessage));
        message.setDirection(direction);
        return message;
    }

    private ShiroUser extractUserFromEvent(Bot bot, MessageEvent event) {
        ShiroUser user = new ShiroUser();
        user.setUserId(event.getUserId());
        user.setAvatarUrl(ShiroUtils.getUserAvatar(event.getUserId(), 0));

        try {
            String userNickname = BotUtils.getUserNickname(bot, event.getUserId());
            user.setNickname(userNickname);
        } catch (Exception e) {
            log.debug("Failed to fetch user nickname for userId: {}", event.getUserId());
            user.setNickname("Unknown User");
        }
        return user;
    }

    private ShiroGroup extractGroupFromEvent(Bot bot, GroupMessageEvent event) {
        ShiroGroup group = null;
        Long groupId = event.getGroupId();
        Long selfId = event.getSelfId();
        Optional<ActionData<GroupInfoResp>> groupInfoOpt = Optional.ofNullable(bot.getGroupInfo(groupId, false));
        if (groupInfoOpt.isPresent() && groupInfoOpt.get().getRetCode() == 0) {
            group = ShiroGroup.convertToShiroGroup(groupInfoOpt.get().getData(), selfId);
        }
        return group;
    }
}
