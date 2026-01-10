package com.github.winefoxbot.core.aop.handler;

import cn.hutool.core.util.RandomUtil;
import com.github.winefoxbot.core.model.entity.ShiroGroup;
import com.github.winefoxbot.core.model.entity.ShiroMessage;
import com.github.winefoxbot.core.model.entity.ShiroUser;
import com.github.winefoxbot.core.model.enums.MessageDirection;
import com.github.winefoxbot.core.model.enums.MessageType;
import com.github.winefoxbot.core.service.shiro.ShiroGroupMembersService;
import com.github.winefoxbot.core.service.shiro.ShiroGroupsService;
import com.github.winefoxbot.core.service.shiro.ShiroMessagesService;
import com.github.winefoxbot.core.service.shiro.ShiroUsersService;
import com.github.winefoxbot.core.utils.BotUtils;
import com.github.winefoxbot.core.utils.MessageConverter;
import com.mikuac.shiro.common.utils.ShiroUtils;
import com.mikuac.shiro.core.Bot;
import com.mikuac.shiro.dto.event.message.AnyMessageEvent;
import com.mikuac.shiro.dto.event.message.GroupMessageEvent;
import com.mikuac.shiro.dto.event.message.MessageEvent;
import com.mikuac.shiro.dto.event.message.PrivateMessageEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ShiroBotAfterCompletionMsgHandler {

    private final ShiroUsersService shiroUsersService;
    private final ShiroGroupsService shiroGroupsService;
    private final ShiroGroupMembersService shiroGroupMembersService;
    private final ShiroMessagesService shiroMessagesService;

    @Async
    public void handle(Bot bot, MessageEvent event) {
        try {
            // 判断消息方向：如果发送者ID等于机器人自身ID，则是发送的消息
            Long selfId = bot.getSelfId();
            Long userId = event.getUserId();
            boolean isSelf = userId != null && userId.equals(selfId);
            MessageDirection direction = isSelf ? MessageDirection.MESSAGE_SENT : MessageDirection.MESSAGE_RECEIVE;

            // 1. 保存或更新用户信息（如果是发送的消息，userId就是Bot自己）
            ShiroUser user = extractUserFromEvent(bot, event);
            shiroUsersService.saveOrUpdate(user);

            // 2. 构建消息实体
            ShiroMessage message = buildShiroMessage(bot, event, direction);

            // 3. 处理群组或私聊特定的逻辑
            if (event instanceof GroupMessageEvent groupEvent) {
                // 保存群组信息
                ShiroGroup group = extractGroupFromEvent(bot, groupEvent);
                shiroGroupsService.saveOrUpdate(group);
                shiroGroupMembersService.saveOrUpdateGroupMemberInfo(groupEvent);

                message.setSessionId(groupEvent.getGroupId());
            } else {
                // 私聊：SessionId设为对方的UserId（若是接收，则是发送者；若是发送，理想情况下应是接收者）
                // 注意：在OneBot标准的私聊自上报事件中，可能需要自行解析target_id，这里暂使用event.getUserId()作为会话ID
                message.setSessionId(event.getUserId());
            }

            // 处理非法 sessionId 的情况
            if (message.getSessionId() < 0 || message.getMessageId() < 0) {
                log.warn("Invalid sessionId or messageId. sessionId: {}, messageId: {}", message.getSessionId(), message.getMessageId());
                return;
            }

            // 4. 保存消息
            shiroMessagesService.save(message);
            // 调整日志级别，避免刷屏，仅记录
            log.debug("Saved {} message ID: {}, Session: {}", direction, message.getMessageId(), message.getSessionId());
        } catch (Exception e) {
            log.error("Error handling message event. event: {}", event, e);
        }
    }


    private ShiroMessage buildShiroMessage(Bot bot, MessageEvent event, MessageDirection direction) {
        ShiroMessage message = new ShiroMessage();

        // 安全提取 Message ID
        long msgId = switch (event) {
            case AnyMessageEvent e -> e.getMessageId() != null ? e.getMessageId().longValue() : -1L;
            case GroupMessageEvent e -> e.getMessageId() != null ? e.getMessageId().longValue() : -1L;
            case PrivateMessageEvent e -> e.getMessageId() != null ? e.getMessageId().longValue() : -1L;
            case null, default -> -1L;
        };

        if (msgId == -1L) {
            msgId = RandomUtil.randomLong();
        }

        message.setMessageId(msgId);
        message.setSelfId(bot.getSelfId());
        message.setMessageType(MessageType.fromValue(event.getMessageType()));
        message.setUserId(event.getUserId());
        message.setMessage(MessageConverter.parseCQToJSONArray(event.getRawMessage()));
        message.setPlainText(MessageConverter.getPlainTextMessage(event.getMessage()));
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
        ShiroGroup group = new ShiroGroup();
        Long groupId = event.getGroupId();
        group.setGroupId(groupId);
        group.setSelfId(event.getSelfId());
        group.setGroupAvatarUrl(ShiroUtils.getGroupAvatar(groupId, 0));

        try {
            String groupName = BotUtils.getGroupName(bot, groupId);
            group.setGroupName(groupName);
        } catch (Exception e) {
            log.debug("Failed to fetch group name for groupId: {}", groupId);
            group.setGroupName("Unknown Group");
        }
        return group;
    }
}
