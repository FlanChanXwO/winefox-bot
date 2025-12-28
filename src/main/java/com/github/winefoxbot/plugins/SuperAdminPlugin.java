package com.github.winefoxbot.plugins;

import com.github.winefoxbot.annotation.PluginFunction;
import com.github.winefoxbot.model.enums.Permission;
import com.mikuac.shiro.annotation.GroupMessageHandler;
import com.mikuac.shiro.annotation.MessageHandlerFilter;
import com.mikuac.shiro.annotation.common.Shiro;
import com.mikuac.shiro.core.Bot;
import com.mikuac.shiro.dto.event.message.AnyMessageEvent;
import com.mikuac.shiro.enums.MsgTypeEnum;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import static com.github.winefoxbot.config.WineFoxBotConfig.COMMAND_PREFIX_REGEX;
import static com.mikuac.shiro.core.BotPlugin.MESSAGE_BLOCK;

/**
 * 管理员插件
 * @author FlanChan (badapple495@outlook.com)
 * @since 2025-12-09-21:39
 */
//@Shiro
//@Component
@Slf4j
public class SuperAdminPlugin {
 /*
    @PluginFunction(group = "超级管理员功能",
            name = "开启机器人",
            permission = Permission.SUPERADMIN,
            description = "在群里开启机器人的使用")
    @GroupMessageHandler
    @MessageHandlerFilter(types = MsgTypeEnum.text, cmd =  "^" + COMMAND_PREFIX_REGEX + "开启机器人$")
    public int enableBotInGroup(Bot bot,   AnyMessageEvent messageEvent) {
        log.info("收到超级管理员开启机器人指令，正在开启机器人...");
        return MESSAGE_BLOCK;
    }

    @PluginFunction(group = "超级管理员功能",
            name = "开启机器人",
            permission = Permission.SUPERADMIN,
            description = "在群里开启机器人的使用")
    @GroupMessageHandler
    @MessageHandlerFilter(types = MsgTypeEnum.text, cmd =  "^" + COMMAND_PREFIX_REGEX + "开启机器人给$")
    public int enableBotForUser(Bot bot, AnyMessageEvent messageEvent) {
        log.info("收到超级管理员开启机器人指令，正在开启机器人...");
        return MESSAGE_BLOCK;
    }

    @PluginFunction(group = "超级管理员功能",
            name = "开启机器人",
            permission = Permission.SUPERADMIN,
            description = "在群里开启机器人的使用")
    @GroupMessageHandler
    @MessageHandlerFilter(types = MsgTypeEnum.text, cmd =  "^" + COMMAND_PREFIX_REGEX + "关闭机器人$")
    public int disableBotInGroup(Bot bot, AnyMessageEvent messageEvent) {
        log.info("收到超级管理员关闭机器人指令，正在关闭机器人...");
        return MESSAGE_BLOCK;
    }

    @PluginFunction(group = "超级管理员功能",
            name = "开启机器人",
            permission = Permission.SUPERADMIN,
            description = "在群里开启机器人的使用")
    @GroupMessageHandler
    @MessageHandlerFilter(types = MsgTypeEnum.text, cmd =  "^" + COMMAND_PREFIX_REGEX + "关闭机器人$")
    public int disableBotForUser(Bot bot, AnyMessageEvent messageEvent) {
        log.info("收到超级管理员关闭机器人指令，正在关闭机器人...");
        return MESSAGE_BLOCK;
    }


 */
}