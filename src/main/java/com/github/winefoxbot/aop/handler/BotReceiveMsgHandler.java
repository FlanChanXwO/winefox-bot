package com.github.winefoxbot.aop.handler;

import cn.hutool.core.util.RandomUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.github.winefoxbot.model.dto.shiro.GroupMemberInfo;
import com.github.winefoxbot.model.entity.ShiroGroup;
import com.github.winefoxbot.model.entity.ShiroGroupMember;
import com.github.winefoxbot.model.entity.ShiroMessage;
import com.github.winefoxbot.model.entity.ShiroUser;
import com.github.winefoxbot.service.shiro.ShiroGroupMembersService;
import com.github.winefoxbot.service.shiro.ShiroGroupsService;
import com.github.winefoxbot.service.shiro.ShiroMessagesService;
import com.github.winefoxbot.service.shiro.ShiroUsersService;
import com.github.winefoxbot.utils.BotUtils;
import com.mikuac.shiro.common.utils.ShiroUtils;
import com.mikuac.shiro.core.Bot;
import com.mikuac.shiro.core.BotContainer;
import com.mikuac.shiro.dto.event.message.AnyMessageEvent;
import com.mikuac.shiro.dto.event.message.GroupMessageEvent;
import com.mikuac.shiro.dto.event.message.MessageEvent;
import com.mikuac.shiro.dto.event.message.PrivateMessageEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class BotReceiveMsgHandler {

    private final ShiroUsersService shiroUsersService;
    private final ShiroGroupsService shiroGroupsService;
    private final ShiroGroupMembersService shiroGroupMembersService;
    private final ShiroMessagesService shiroMessagesService;
    private final BotContainer botContainer;

    @Async
    public void handle(Bot bot, MessageEvent event) {
        // 1. Save or Update User
        ShiroUser user = extractUserFromEvent(event);
        shiroUsersService.saveOrUpdate(user);

        // 2. Save Message
        ShiroMessage message = new ShiroMessage();
        switch (event) {
            case AnyMessageEvent e -> message.setMessageId(Long.valueOf(e.getMessageId()));
            case GroupMessageEvent e -> message.setMessageId(Long.valueOf(e.getMessageId()));
            case PrivateMessageEvent e -> message.setMessageId(Long.valueOf(e.getMessageId()));
            default -> message.setMessageId(RandomUtil.randomLong());
        }
        message.setSelfId(bot.getSelfId());
        message.setMessageType(event.getMessageType());
        message.setUserId(event.getUserId());
        message.setMessage(JSONUtil.parseArray(BotUtils.parseCQtoJsonStr(event.getRawMessage())));

        log.info("Received message: {}", message.getMessage());
        message.setPlainText(BotUtils.getPlainTextMessage(event.getMessage()));
        message.setDirection("message");

        // 3. Handle Group-specific data if it's a group message
        if (event instanceof GroupMessageEvent groupEvent) {
            message.setGroupId(groupEvent.getGroupId());
            ShiroGroup group = extractGroupFromEvent(groupEvent);
            shiroGroupsService.saveOrUpdate(group);
            shiroGroupMembersService.saveOrUpdateGroupMemberInfo(groupEvent);
        }

        shiroMessagesService.save(message);
    }


    private ShiroUser extractUserFromEvent(MessageEvent event) {
        ShiroUser user = new ShiroUser();
        user.setUserId(event.getUserId());
        Optional<Bot> bot = botContainer.robots.values().stream().findFirst();
        if (bot.isPresent()) {
            Bot firstBot = bot.get();
            String userNickname = BotUtils.getUserNickname(firstBot, event.getUserId());
            user.setNickname(userNickname);
        } else {
            throw new RuntimeException("No bot available to fetch user nickname");
        }
        user.setAvatarUrl(ShiroUtils.getUserAvatar(event.getUserId(), 0));
        return user;
    }

    private ShiroGroup extractGroupFromEvent(GroupMessageEvent event) {
        ShiroGroup group = new ShiroGroup();
        Long groupId = event.getGroupId();
        group.setGroupId(groupId);
        Optional<Bot> bot = botContainer.robots.values().stream().findFirst();
        if (bot.isPresent()) {
            Bot firstBot = bot.get();
            String groupNickname = BotUtils.getGroupName(firstBot, event.getGroupId());
            group.setGroupName(groupNickname);
        } else {
            throw new RuntimeException("No bot available to fetch user nickname");
        }
        group.setGroupAvatarUrl(ShiroUtils.getGroupAvatar(groupId, 0));
        return group;
    }
}