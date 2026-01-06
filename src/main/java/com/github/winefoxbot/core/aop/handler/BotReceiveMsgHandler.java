package com.github.winefoxbot.core.aop.handler;

import cn.hutool.core.util.RandomUtil;
import cn.hutool.json.JSONUtil;
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
public class BotReceiveMsgHandler {

    private final ShiroUsersService shiroUsersService;
    private final ShiroGroupsService shiroGroupsService;
    private final ShiroGroupMembersService shiroGroupMembersService;
    private final ShiroMessagesService shiroMessagesService;

    @Async
    public void handle(Bot bot, MessageEvent event) {

        try {
            // 1. Save or Update User (直接使用传入的 bot 实例)
            ShiroUser user = extractUserFromEvent(bot, event);
            shiroUsersService.saveOrUpdate(user);
            // 2. Build Message
            ShiroMessage message = buildShiroMessage(bot, event);

            // 3. Handle Group Specific Logic
            if (event instanceof GroupMessageEvent groupEvent) {
                // 处理群组信息
                ShiroGroup group = extractGroupFromEvent(bot, groupEvent);
                shiroGroupsService.saveOrUpdate(group);
                shiroGroupMembersService.saveOrUpdateGroupMemberInfo(groupEvent);

                message.setSessionId(groupEvent.getGroupId());
            } else {
                message.setSessionId(event.getUserId());
            }

            // 4. Save Message
            shiroMessagesService.save(message);
            log.info("Saved message ID: {}, Content: {}", message.getMessageId(), message.getPlainText());

        } catch (Exception e) {
            log.error("Error handling received message. event: {}", event, e);
        }
    }


    private ShiroMessage buildShiroMessage(Bot bot, MessageEvent event) {
        ShiroMessage message = new ShiroMessage();

        // 提取 Message ID
        long msgId = switch (event) {
            case AnyMessageEvent e -> e.getMessageId().longValue();
            case GroupMessageEvent e -> e.getMessageId().longValue();
            case PrivateMessageEvent e -> e.getMessageId().longValue();
            case null, default -> RandomUtil.randomLong();
        };

        message.setMessageId(msgId);
        message.setSelfId(bot.getSelfId());
        message.setMessageType(MessageType.fromValue(event.getMessageType()));
        message.setUserId(event.getUserId());
        message.setMessage(JSONUtil.parseArray(BotUtils.parseCQtoJsonStr(event.getRawMessage(), true)));
        message.setPlainText(BotUtils.getPlainTextMessage(event.getMessage()));
        message.setDirection(MessageDirection.MESSAGE_RECEIVE);
        return message;
    }

    private ShiroUser extractUserFromEvent(Bot bot, MessageEvent event) {
        ShiroUser user = new ShiroUser();
        user.setUserId(event.getUserId());
        user.setAvatarUrl(ShiroUtils.getUserAvatar(event.getUserId(), 0));

        // 直接使用传入的 bot 获取信息，无需从容器查找
        try {
            String userNickname = BotUtils.getUserNickname(bot, event.getUserId());
            user.setNickname(userNickname);
        } catch (Exception e) {
            log.warn("Failed to fetch user nickname for userId: {}", event.getUserId());
            user.setNickname("Unknown User"); // 降级处理
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
            log.warn("Failed to fetch group name for groupId: {}", groupId);
            group.setGroupName("Unknown Group");
        }
        return group;
    }
}