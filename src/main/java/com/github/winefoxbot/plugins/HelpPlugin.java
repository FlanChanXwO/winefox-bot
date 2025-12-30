package com.github.winefoxbot.plugins;

import com.github.winefoxbot.annotation.PluginFunction;
import com.github.winefoxbot.model.enums.Permission;
import com.github.winefoxbot.service.core.HelpImageService;
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

@Shiro
@Component
@Slf4j
@RequiredArgsConstructor
public class HelpPlugin {
    private final HelpImageService helpImageService;

    @PluginFunction(
            group = "核心功能", name = "帮助文档",
            description = "生成并发送帮助图片，展示所有可用功能及其说明。", permission = Permission.USER,
            commands = {"/help", "/h", "/wf帮助"})
    @AnyMessageHandler
    @MessageHandlerFilter(types = MsgTypeEnum.text, cmd = "^/(支援|help|h|酒狐的特殊能力|wf帮助)(?:\\s+(.+))?$")
    public void helpCommand(Bot bot, AnyMessageEvent event, Matcher matcher) {
        try {
            log.info("正在生成帮助图片...");
            String param = matcher.group(2);
            byte[] imageBytes = (param != null)
                    ? helpImageService.generateHelpImageByGroup(param)
                    : helpImageService.generateAllHelpImage();
            if (imageBytes == null) {
                log.warn("请求的帮助分组 '{}' 不存在，无法生成帮助图片。", param);
                bot.sendMsg(event, "抱歉，未找到您请求的分组。", false);
                return;
            }
            log.info("帮助图片生成完毕，大小: {} bytes。准备发送...", imageBytes.length);
            bot.sendMsg(event, MsgUtils.builder().img(imageBytes).build(), false);
        } catch (Exception e) {
            log.error("生成帮助图片时发生未知错误", e);
            bot.sendMsg(event, "抱歉，生成帮助图片时发生未知错误，请稍后再试。", false);
        }
    }
}
