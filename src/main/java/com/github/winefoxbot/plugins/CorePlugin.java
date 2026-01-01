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

import static com.github.winefoxbot.config.WineFoxBotConfig.COMMAND_PREFIX_REGEX;
import static com.github.winefoxbot.config.WineFoxBotConfig.COMMAND_SUFFIX_REGEX;

/**
 * @author FlanChan (badapple495@outlook.com)
 * @since 2025-12-08-0:17
 */
@Shiro
@Component
@Slf4j
@RequiredArgsConstructor
public class CorePlugin {

    @PluginFunction(group = "核心功能", name = "关闭或开启机器人", description = "关闭或开启机器人的功能，关闭后该机器人不再处理任何事件（包括推送）",  permission = Permission.SUPERADMIN)
    @AnyMessageHandler
    @MessageHandlerFilter(types = MsgTypeEnum.text, cmd = COMMAND_PREFIX_REGEX  + "(开启机器人|关闭机器人)" + COMMAND_SUFFIX_REGEX)
    public void botss(Bot bot, AnyMessageEvent event, Matcher matcher) {

    }

}