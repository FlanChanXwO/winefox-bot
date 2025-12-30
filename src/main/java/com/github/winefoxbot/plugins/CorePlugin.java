package com.github.winefoxbot.plugins;

import com.github.winefoxbot.annotation.PluginFunction;
import com.github.winefoxbot.model.enums.Permission;
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

import java.util.regex.Matcher;

/**
 * @author FlanChan (badapple495@outlook.com)
 * @since 2025-12-08-0:17
 */
@Shiro
@Component
@Slf4j
@RequiredArgsConstructor
public class CorePlugin {

    @PluginFunction(group = "测试", name = "echo模块", description = "回复收到的消息内容",            hidden = true, permission = Permission.SUPERADMIN)
    @AnyMessageHandler
    @MessageHandlerFilter(types = MsgTypeEnum.text,   cmd = "/echo\\s.+$")
    public void echo(Bot bot, AnyMessageEvent event, Matcher matcher) {
        String messageContent = matcher.group(0);
        log.info("echo模块 接收到 {}", messageContent);
        bot.sendMsg(event, MsgUtils.builder()
                        .at(event.getUserId())
                        .text(messageContent)
                        .build(), false);
    }

}