package com.github.winefoxbot.service.bot.impl;

import com.github.winefoxbot.model.dto.GroupEventMessage;
import com.github.winefoxbot.service.bot.BotReplyService;
import com.github.winefoxbot.service.bot.QQGroupService;
import com.mikuac.shiro.common.utils.MsgUtils;
import com.mikuac.shiro.core.Bot;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * @author FlanChan (badapple495@outlook.com)
 * @since 2025-12-04-21:17
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class QQGroupServiceImpl implements QQGroupService {

    private final BotReplyService botReplyService;

    @Override
    public void handleWelcomeMessage(Bot bot, GroupEventMessage eventMessage) {
        // 获取模板
        BotReplyServiceImpl.Reply welcomeReplyTemplate = botReplyService.getWelcomeReply(eventMessage.getUsername());
        // 构建消息
        String message = MsgUtils.builder()
                .at(eventMessage.getUserId())
                .text(welcomeReplyTemplate.text())
                .img(welcomeReplyTemplate.picture())
                .build();
        // 发送消息
        bot.sendGroupMsg(eventMessage.getGroupId(), message, false);
    }

    @Override
    public void handleFarewellMessage(Bot bot, GroupEventMessage eventMessage) {
        // 获取模板
        BotReplyServiceImpl.Reply farewellReplyTemplate = botReplyService.getFarewellReply(eventMessage.getUsername());
        // 构建消息
        String message = MsgUtils.builder()
                .text(farewellReplyTemplate.text())
                .img(farewellReplyTemplate.picture())
                .build();
        // 发送消息
        bot.sendGroupMsg(eventMessage.getGroupId(), message, false);
    }

    @Override
    public void handleStopMessage(Bot bot, GroupEventMessage eventMessage) {
        // 获取模板
        BotReplyServiceImpl.Reply stopReplyTemplate = botReplyService.getMasterStopReply(eventMessage.getUsername());
        // 构建消息
        String message = MsgUtils.builder()
                .at(eventMessage.getUserId())
                .text(stopReplyTemplate.text())
                .img(stopReplyTemplate.picture())
                .build();
        // 发送消息
        bot.sendGroupMsg(eventMessage.getGroupId(), message, false);
    }

}