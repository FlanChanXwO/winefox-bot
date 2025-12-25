package com.github.winefoxbot.plugins;

import com.github.winefoxbot.annotation.Limit;
import com.github.winefoxbot.config.WineFoxBotConfig;
import com.github.winefoxbot.service.bot.BotReplyService;
import com.github.winefoxbot.service.bot.impl.BotReplyServiceImpl;
import com.github.winefoxbot.utils.BotUtils;
import com.github.winefoxbot.utils.FileUtil;
import com.mikuac.shiro.annotation.AnyMessageHandler;
import com.mikuac.shiro.annotation.MessageHandlerFilter;
import com.mikuac.shiro.annotation.common.Shiro;
import com.mikuac.shiro.common.utils.MsgUtils;
import com.mikuac.shiro.core.Bot;
import com.mikuac.shiro.dto.event.message.AnyMessageEvent;
import com.mikuac.shiro.enums.MsgTypeEnum;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.FileNotFoundException;
import java.util.regex.Matcher;

/**
 * @author FlanChan (badapple495@outlook.com)
 * @since 2025-12-08-0:17
 */
@Shiro
@Component
@Slf4j
@RequiredArgsConstructor
@Limit(timeInSeconds = 10, notificationIntervalSeconds = 60)
public class TestPlugin {

    private final BotReplyService botReplyService;
    private final WineFoxBotConfig wineFoxBotConfig;


    @AnyMessageHandler
    @MessageHandlerFilter(cmd = "^\\/echo\\s.+$")
    public void echo(Bot bot, AnyMessageEvent event) {
        String messageContent = event.getRawMessage().substring(5).trim();
        log.info("echo模块 接收到 {}", messageContent);
        bot.sendMsg(event, MsgUtils.builder()
                        .at(event.getUserId())
                        .text(messageContent)
                        .build(), false);
    }

    @AnyMessageHandler
    @MessageHandlerFilter(types = MsgTypeEnum.text, cmd = "^/sendAudio(?:\\s+(\\S+))?$")
    public void sendAudio(Bot bot, AnyMessageEvent event, Matcher matcher) throws FileNotFoundException {
        String messageContent = event.getRawMessage().substring(5).trim();
        Long groupId = Long.parseLong(matcher.group(1));
        log.info("echo模块 接收到 {}", messageContent);
        bot.sendGroupMsg(groupId, MsgUtils.builder()
                .voice(FileUtil.getFileUrlPrefix() + wineFoxBotConfig.getTestPath())
                .build(), false);
    }


    @AnyMessageHandler
    @MessageHandlerFilter(cmd = "^/测试欢迎消息$")
    public void testWelcomeMessage(Bot bot, AnyMessageEvent event) {
        String userNickname = event.getGroupId() == null ? BotUtils.getUserNickname(bot, event.getUserId()) : BotUtils.getGroupMemberNickname(bot, event.getGroupId(), event.getUserId(), false);
        BotReplyServiceImpl.Reply welcomeReplyTemplate = botReplyService.getWelcomeReply(userNickname);
        // 构建消息
        String message = MsgUtils.builder()
                .at(event.getUserId())
                .text(welcomeReplyTemplate.text())
                .img(welcomeReplyTemplate.picture())
                .build();
        // 发送消息
        bot.sendMsg(event, message, false);
    }


}