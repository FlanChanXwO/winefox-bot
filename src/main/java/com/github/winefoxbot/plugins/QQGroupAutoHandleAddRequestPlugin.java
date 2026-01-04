package com.github.winefoxbot.plugins;

import com.github.winefoxbot.annotation.PluginFunction;
import com.github.winefoxbot.config.app.WineFoxBotConfig;
import com.github.winefoxbot.model.entity.QQGroupAutoHandleAddRequestFeatureConfig;
import com.github.winefoxbot.model.enums.Permission;
import com.github.winefoxbot.service.qqgroup.QQGroupService;
import com.mikuac.shiro.annotation.GroupMessageHandler;
import com.mikuac.shiro.annotation.MessageHandlerFilter;
import com.mikuac.shiro.annotation.common.Shiro;
import com.mikuac.shiro.core.Bot;
import com.mikuac.shiro.dto.event.message.GroupMessageEvent;
import com.mikuac.shiro.enums.MsgTypeEnum;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.regex.Matcher;

import static com.github.winefoxbot.config.app.WineFoxBotConfig.COMMAND_PREFIX_REGEX;
import static com.github.winefoxbot.config.app.WineFoxBotConfig.COMMAND_SUFFIX_REGEX;

/**
 * @author FlanChan (badapple495@outlook.com)
 * @since 2025-12-26-21:35
 */
@Shiro
@Component
@RequiredArgsConstructor
public class QQGroupAutoHandleAddRequestPlugin {
    private final QQGroupService qqGroupService;


    @PluginFunction(name = "自动处理加群请求", description = "自动处理加群请求功能开关指令，指令：/开启自动处理加群 | /关闭自动处理加群，需在群内使用",
            hidden = true,
            permission = Permission.ADMIN, commands = {"开启自动处理加群", "关闭自动处理加群"})
    @GroupMessageHandler
    @MessageHandlerFilter(types = MsgTypeEnum.text, cmd = COMMAND_PREFIX_REGEX + "(开启自动处理加群|关闭自动处理加群)" + COMMAND_SUFFIX_REGEX)
    public void toggleAutoHandleAddRequest(Bot bot, GroupMessageEvent event) {
        String msg = event.getMessage().replace(COMMAND_PREFIX_REGEX, "");
        Long groupId = event.getGroupId();
        QQGroupAutoHandleAddRequestFeatureConfig config = qqGroupService.getOrCreateAutoHandleAddRequestConfig(groupId);// 确保配置存在
        // 功能开关指令
        if ("开启自动处理加群".equals(msg)) {
            if (config.getAutoHandleAddRequestEnabled()) {
                bot.sendGroupMsg(groupId, "自动处理加群请求功能当前已经开启", false);
                return;
            }
            qqGroupService.toggleAutoHandleAddRequestFeature(groupId, true,config);
            bot.sendGroupMsg(groupId, "已开启自动处理加群请求功能", false);
        } else if ("关闭自动处理加群".equals(msg)) {
            if(!config.getAutoHandleAddRequestEnabled()) {
                bot.sendGroupMsg(groupId, "自动处理加群请求功能当前已经关闭", false);
                return;
            }
            qqGroupService.toggleAutoHandleAddRequestFeature(groupId, false,config);
            bot.sendGroupMsg(groupId, "已关闭自动处理加群请求功能", false);
        }
    }
}