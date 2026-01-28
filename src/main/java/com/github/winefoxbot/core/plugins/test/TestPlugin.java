package com.github.winefoxbot.core.plugins.test;

import com.github.winefoxbot.core.actionpath.napcat.CustomFaceActionPath;
import com.github.winefoxbot.core.annotation.plugin.PluginFunction;
import com.github.winefoxbot.core.model.enums.common.Permission;
import com.mikuac.shiro.annotation.AnyMessageHandler;
import com.mikuac.shiro.annotation.MessageHandlerFilter;
import com.mikuac.shiro.annotation.common.Shiro;
import com.mikuac.shiro.common.utils.MsgUtils;
import com.mikuac.shiro.common.utils.OneBotMedia;
import com.mikuac.shiro.core.Bot;
import com.mikuac.shiro.dto.action.common.ActionData;
import com.mikuac.shiro.dto.event.message.AnyMessageEvent;
import com.mikuac.shiro.enums.ActionPathEnum;
import com.mikuac.shiro.enums.MsgTypeEnum;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.regex.Matcher;

/**
 * @author FlanChan (badapple495@outlook.com)
 * @since 2025-12-08-0:17
 */
@Shiro
@Component
@Slf4j
@RequiredArgsConstructor
public class TestPlugin {

    @PluginFunction(name = "echo模块", description = "回复收到的消息内容", hidden = true, permission = Permission.SUPERADMIN)
    @AnyMessageHandler
    @MessageHandlerFilter(types = MsgTypeEnum.text, cmd = "^/echo(?:\\s+(\\S+))?$")
    public void echo(Bot bot, AnyMessageEvent event, Matcher matcher) {
        String args = matcher.group(1);
        bot.sendMsg(event, args, false);
    }

    @AnyMessageHandler
    @MessageHandlerFilter(types = MsgTypeEnum.text, cmd = "^/emoji$")
    public void emoji(Bot bot, AnyMessageEvent event) {
        bot.sendMsg(event, MsgUtils.builder()
                .img(new OneBotMedia())
                .build(), false);
    }

    @AnyMessageHandler
    @MessageHandlerFilter(types = MsgTypeEnum.text, cmd = "^/emojis$")
    public void emojis(Bot bot, AnyMessageEvent event) {
        ActionData<List<String>> actionData = bot.customRequest(CustomFaceActionPath.FETCH_CUSTOM_FACE, CustomFaceActionPath.FetchCustomFaceParams.builder().count(50).build().toParamMap());
        List<String> data = actionData.getData();
        bot.sendMsg(event, MsgUtils.builder()
                        .text(StringUtils.join(data,"\n"))
                .build(), false);
    }

}