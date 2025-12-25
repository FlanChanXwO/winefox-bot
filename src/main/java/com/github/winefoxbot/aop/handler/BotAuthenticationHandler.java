package com.github.winefoxbot.aop.handler;

import com.github.winefoxbot.config.WineFoxBotConfig;
import com.github.winefoxbot.service.bot.BotReplyService;
import com.github.winefoxbot.service.bot.impl.BotReplyServiceImpl;
import com.github.winefoxbot.utils.BotUtils;
import com.mikuac.shiro.common.utils.MsgUtils;
import com.mikuac.shiro.core.Bot;
import com.mikuac.shiro.dto.event.message.GroupMessageEvent;
import com.mikuac.shiro.dto.event.message.MessageEvent;
import com.mikuac.shiro.dto.event.message.PrivateMessageEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class BotAuthenticationHandler {

    private final WineFoxBotConfig wineFoxBotConfig;

    private final BotReplyService botReplyService;

    public boolean handle(Bot bot, MessageEvent event) {
        // 获取触发事件的插件名称
        String message = event.getMessage();
        if (event instanceof GroupMessageEvent e) {
            // 检查是否为需要拦截的插件
            if (wineFoxBotConfig.matchBlockCommand(message) && !wineFoxBotConfig.getMaster().equals(event.getUserId())) {
                BotReplyServiceImpl.Reply reply = botReplyService.getMasterStopReply(BotUtils.getGroupMemberNickname(bot,e.getGroupId(), e.getUserId(),false));
                bot.sendGroupMsg(e.getGroupId(), MsgUtils.builder()
                        .text(reply.text())
                        .img(reply.picture())
                        .build(), false);
                return false;
            }
            // 检查是否在允许的群组中
            List<Long> allowGroups = wineFoxBotConfig.getAllowGroups();
            return e.getGroupId() == null || allowGroups.contains(e.getGroupId());
        } else if (event instanceof PrivateMessageEvent e) {
            // 检查是否为需要拦截的插件
            if (wineFoxBotConfig.matchBlockCommand(message) && !wineFoxBotConfig.getMaster().equals(event.getUserId())) {
                BotReplyServiceImpl.Reply reply = botReplyService.getMasterStopReply(BotUtils.getUserNickname(bot,e.getUserId()));
                bot.sendPrivateMsg(e.getUserId(), MsgUtils.builder()
                        .text(reply.text())
                        .img(reply.picture())
                        .build(), false);
                return false;
            }
        } else { // guild 事件忽略
            return false;
        }
        return true;
    }


}