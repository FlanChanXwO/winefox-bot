package com.github.winefoxbot.aop.handler;

import cn.hutool.core.util.RandomUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.github.winefoxbot.model.entity.ShiroGroup;
import com.github.winefoxbot.model.entity.ShiroGroupMember;
import com.github.winefoxbot.model.entity.ShiroMessage;
import com.github.winefoxbot.model.entity.ShiroUser;
import com.github.winefoxbot.service.shiro.ShiroGroupMembersService;
import com.github.winefoxbot.service.shiro.ShiroGroupsService;
import com.github.winefoxbot.service.shiro.ShiroMessagesService;
import com.github.winefoxbot.service.shiro.ShiroUsersService;
import com.github.winefoxbot.utils.BotUtils;
import com.google.gson.Gson;
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
    private final Gson gson;

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
        message.setTime(LocalDateTime.ofInstant(Instant.ofEpochSecond(event.getTime()), ZoneId.systemDefault()));
        message.setSelfId(bot.getSelfId());
        message.setMessageType(event.getMessageType());
        message.setUserId(event.getUserId());
        message.setMessage(JSONUtil.parseArray(BotUtils.parseCQtoJsonStr(event.getRawMessage())));

        log.info("Received message: {}", message.getMessage());
        message.setPlainText(BotUtils.getPlainTextMessage(event.getMessage()));
        message.setDirection("message");

        System.out.println(message);

        // 3. Handle Group-specific data if it's a group message
        if (event instanceof GroupMessageEvent groupEvent) {
            message.setGroupId(groupEvent.getGroupId());

            // Save or Update Group
            ShiroGroup group = extractGroupFromEvent(groupEvent);
            shiroGroupsService.saveOrUpdate(group);

            // Save or Update Group Member
            ShiroGroupMember newMemberData = extractGroupMemberFromEvent(groupEvent);

            // Check if member already exists based on composite key (group_id, user_id)
            LambdaQueryWrapper<ShiroGroupMember> queryWrapper = shiroGroupMembersService.lambdaQuery().getWrapper()
                    .eq(ShiroGroupMember::getGroupId, newMemberData.getGroupId())
                    .eq(ShiroGroupMember::getUserId, newMemberData.getUserId());
            ShiroGroupMember existingMember = shiroGroupMembersService.getOne(queryWrapper);

            if (existingMember != null) {
                existingMember.setMemberNickname(newMemberData.getMemberNickname());
                existingMember.setLastUpdated(newMemberData.getLastUpdated());
                LambdaUpdateWrapper<ShiroGroupMember> wrapper = shiroGroupMembersService.lambdaUpdate().getWrapper()
                        .eq(ShiroGroupMember::getGroupId, existingMember.getGroupId())
                        .eq(ShiroGroupMember::getUserId, existingMember.getUserId());
                shiroGroupMembersService.getBaseMapper().update(existingMember, wrapper);
            } else {
                shiroGroupMembersService.save(newMemberData);
            }
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
        user.setLastUpdated(LocalDateTime.now());
        return user;
    }

    private ShiroGroup extractGroupFromEvent(GroupMessageEvent event) {
        ShiroGroup group = new ShiroGroup();
        group.setGroupId(event.getGroupId());
        Optional<Bot> bot = botContainer.robots.values().stream().findFirst();
        if (bot.isPresent()) {
            Bot firstBot = bot.get();
            String groupNickname = BotUtils.getGroupName(firstBot, event.getGroupId());
            group.setGroupName(groupNickname);
        } else {
            throw new RuntimeException("No bot available to fetch user nickname");
        }
        group.setGroupAvatarUrl(ShiroUtils.getGroupAvatar(event.getGroupId(), 0));
        group.setLastUpdated(LocalDateTime.now());
        return group;
    }

    private ShiroGroupMember extractGroupMemberFromEvent(GroupMessageEvent event) {
        GroupMessageEvent.GroupSender sender = event.getSender();
        ShiroGroupMember member = new ShiroGroupMember();
        member.setGroupId(event.getGroupId());
        member.setUserId(event.getUserId());
        member.setMemberNickname(sender.getNickname());
        member.setLastUpdated(LocalDateTime.now());
        return member;
    }
}