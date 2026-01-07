package com.github.winefoxbot.core.plugins;

import cn.hutool.core.util.RandomUtil;
import com.github.winefoxbot.core.actionpath.napcat.CustomFaceActionPath;
import com.github.winefoxbot.core.annotation.PluginFunction;
import com.github.winefoxbot.core.exception.bot.CommandParseException;
import com.github.winefoxbot.core.model.enums.Permission;
import com.mikuac.shiro.annotation.AnyMessageHandler;
import com.mikuac.shiro.annotation.MessageHandlerFilter;
import com.mikuac.shiro.annotation.common.Shiro;
import com.mikuac.shiro.common.utils.MsgUtils;
import com.mikuac.shiro.core.Bot;
import com.mikuac.shiro.dto.action.common.ActionData;
import com.mikuac.shiro.dto.event.message.AnyMessageEvent;
import com.mikuac.shiro.enums.MsgTypeEnum;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.regex.Matcher;

import static com.github.winefoxbot.core.config.app.WineFoxBotConfig.COMMAND_PREFIX_REGEX;
import static com.github.winefoxbot.core.config.app.WineFoxBotConfig.COMMAND_SUFFIX_REGEX;

/**
 * @author FlanChan (badapple495@outlook.com)
 * @since 2025-12-08-0:17
 */
@Shiro
@Component
@Slf4j
@RequiredArgsConstructor
public class TestPlugin {

    @PluginFunction( name = "echo模块", description = "回复收到的消息内容", hidden = true, permission = Permission.SUPERADMIN)
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

    @PluginFunction( name = "异常测试", description = "回复收到的消息内容", hidden = true, permission = Permission.SUPERADMIN)
    @AnyMessageHandler
    @MessageHandlerFilter(types = MsgTypeEnum.text, cmd = COMMAND_PREFIX_REGEX + "throw" + COMMAND_SUFFIX_REGEX)
    public void exceptionTest(Bot bot, AnyMessageEvent event) {
        throw new CommandParseException(bot, event,"这是一个测试用的命令解析异常",null);
    }

    @PluginFunction( name = "异常测试", description = "回复收到的消息内容", hidden = true, permission = Permission.SUPERADMIN)
    @AnyMessageHandler
    @MessageHandlerFilter(types = MsgTypeEnum.text, cmd = COMMAND_PREFIX_REGEX + "em" + COMMAND_SUFFIX_REGEX)
    public void customFetchTest(Bot bot, AnyMessageEvent event) {
        ActionData<List<String>> actionData = bot.customRequest(CustomFaceActionPath.FETCH_CUSTOM_FACE, CustomFaceActionPath.FetchCustomFaceParams.builder()
                .count(40)
                .build()
                .toParamMap());
        if (actionData.getRetCode() == 0) {
            List<String> data = actionData.getData();
            System.out.println(data);
            bot.sendMsg(event,MsgUtils.builder().img(RandomUtil.randomEle(data)).build(),false);
        }
    }
}