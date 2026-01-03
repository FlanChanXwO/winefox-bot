package com.github.winefoxbot.plugins;

import com.github.winefoxbot.annotation.PluginFunction;
import com.github.winefoxbot.exception.bot.CommandParseException;
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

import static com.github.winefoxbot.config.app.WineFoxBotConfig.COMMAND_PREFIX_REGEX;
import static com.github.winefoxbot.config.app.WineFoxBotConfig.COMMAND_SUFFIX_REGEX;

/**
 * @author FlanChan (badapple495@outlook.com)
 * @since 2025-12-08-0:17
 */
@Shiro
@Component
@Slf4j
@RequiredArgsConstructor
public class TestPlugin {

    @PluginFunction(group = "测试", name = "echo模块", description = "回复收到的消息内容", hidden = true, permission = Permission.SUPERADMIN)
    @AnyMessageHandler
    @MessageHandlerFilter(types = MsgTypeEnum.text, cmd = COMMAND_PREFIX_REGEX + "echo(?:\\s+(\\S+))?" + COMMAND_SUFFIX_REGEX)
    public void echo(Bot bot, AnyMessageEvent event, Matcher matcher) {
            String messageContent = matcher.group(1);
            log.info("echo模块 接收到 {}", messageContent);
            bot.sendMsg(event, MsgUtils.builder()
                    .at(event.getUserId())
                    .text(" " +messageContent)
                    .build(), false);
    }

    @PluginFunction(group = "测试", name = "异常测试", description = "回复收到的消息内容", hidden = true, permission = Permission.SUPERADMIN)
    @AnyMessageHandler
    @MessageHandlerFilter(types = MsgTypeEnum.text, cmd = COMMAND_PREFIX_REGEX + "throw" + COMMAND_SUFFIX_REGEX)
    public void exceptionTest(Bot bot, AnyMessageEvent event) {
        throw new CommandParseException(bot, event,"这是一个测试用的命令解析异常");
    }
}