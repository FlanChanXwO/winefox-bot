package com.github.winefoxbot.plugins;

import com.github.winefoxbot.annotation.PluginFunction;
import com.mikuac.shiro.annotation.AnyMessageHandler;
import com.mikuac.shiro.annotation.MessageHandlerFilter;
import com.mikuac.shiro.annotation.common.Shiro;
import com.mikuac.shiro.core.Bot;
import com.mikuac.shiro.dto.event.message.AnyMessageEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 管理员插件
 * @author FlanChan (badapple495@outlook.com)
 * @since 2025-12-09-21:39
 */
@Shiro
@Component
@Slf4j
public class AdminPlugin{


    @PluginFunction(
            group = "管理功能",
            name = "发送群消息",
            permission = "超级管理员",
            description = "使用 /sendGroupMsg ${GID} ${msg} 命令向指定群发送消息。"
    )
    @AnyMessageHandler
    @MessageHandlerFilter(cmd = "^/sendGroupMsg\\s.+$")
    public void sendGroupMessage(Bot bot, AnyMessageEvent event) {
        String message = event.getRawMessage().replaceFirst("^/sendGroupMsg\\s", "");
        if (!message.matches("^\\d+\\s.+$")) {
            bot.sendGroupMsg(event.getGroupId(), "命令格式错误，请使用：/sendGroupMsg ${GID} ${msg}", false);
            return;
        }
        String[] parts = message.split("\\s", 2);
        long targetGroupId = Long.parseLong(parts[0]);
        String msg = parts[1];
        bot.sendGroupMsg(targetGroupId, msg, false);
    }

    @PluginFunction(group = "管理功能",
            name = "发送私聊消息",
            permission = "超级管理员",
            description = "使用 /sendPrivateMsg ${UID} ${msg} 命令向指定用户发送私聊消息。"
    )
    @AnyMessageHandler
    @MessageHandlerFilter(cmd = "^/sendPrivateMsg\\s.+$")
    public void sendPrivateMessage(Bot bot, AnyMessageEvent event) {
        String message = event.getRawMessage().replaceFirst("^/sendPrivateMsg\\s", "");
        if (!message.matches("^\\d+\\s.+$")) {
            bot.sendPrivateMsg(event.getUserId(), "命令格式错误，请使用：/sendPrivateMsg \"UID\" \"msg\"", false);
            return;
        }
        String[] parts = message.split("\\s", 2);
        long targetUserId = Long.parseLong(parts[0]);
        String msg = parts[1];
        bot.sendPrivateMsg(targetUserId, msg, false);
    }
}