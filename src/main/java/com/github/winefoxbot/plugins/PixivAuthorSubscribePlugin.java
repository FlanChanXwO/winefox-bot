package com.github.winefoxbot.plugins;

import com.github.winefoxbot.annotation.PluginFunction;
import com.github.winefoxbot.model.enums.Permission;
import com.github.winefoxbot.service.pixiv.PixivAuthorSubscriptionService;
import com.mikuac.shiro.annotation.AnyMessageHandler;
import com.mikuac.shiro.annotation.MessageHandlerFilter;
import com.mikuac.shiro.annotation.common.Shiro;
import com.mikuac.shiro.core.Bot;
import com.mikuac.shiro.dto.event.message.AnyMessageEvent;
import com.mikuac.shiro.enums.MsgTypeEnum;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import static com.github.winefoxbot.config.app.WineFoxBotConfig.*;

/**
 * @author FlanChan (badapple495@outlook.com)
 * @since 2025-12-29-18:41
 */
@Shiro
@Component
@RequiredArgsConstructor
public class PixivAuthorSubscribePlugin {
    private final PixivAuthorSubscriptionService pixivAuthorSubscriptionService;

    @PluginFunction(name = "订阅作者" , hidden = true, description = "订阅作者更新，当有新的更新时，会进行推送，根据分级会进行选择性包装，你可以用另一个命令设置推送地点",
            permission = Permission.USER,
            commands = {
                    COMMAND_PREFIX + "P站作者订阅" + COMMAND_SUFFIX,
            })
    @AnyMessageHandler
    @MessageHandlerFilter(types = MsgTypeEnum.text, cmd =  COMMAND_PREFIX_REGEX + "P站作者订阅" +  COMMAND_SUFFIX_REGEX)
    public void toggleAutoRevoke(Bot bot, AnyMessageEvent event) {

    }
}