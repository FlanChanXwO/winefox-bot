package com.github.winefoxbot.plugins;

import com.github.winefoxbot.annotation.PluginFunction;
import com.github.winefoxbot.config.WineFoxBotConfig;
import com.github.winefoxbot.model.entity.SetuConfig;
import com.github.winefoxbot.model.enums.Permission;
import com.github.winefoxbot.model.enums.SessionType;
import com.github.winefoxbot.service.pixiv.PixivAuthorSubscriptionService;
import com.mikuac.shiro.annotation.AnyMessageHandler;
import com.mikuac.shiro.annotation.MessageHandlerFilter;
import com.mikuac.shiro.annotation.common.Shiro;
import com.mikuac.shiro.core.Bot;
import com.mikuac.shiro.dto.event.message.AnyMessageEvent;
import com.mikuac.shiro.enums.MsgTypeEnum;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * @author FlanChan (badapple495@outlook.com)
 * @since 2025-12-29-18:41
 */
@Shiro
@Component
@RequiredArgsConstructor
public class PixivAuthorSubscribePlugin {
    private final PixivAuthorSubscriptionService pixivAuthorSubscriptionService;

    @PluginFunction(group = "Pixiv", name = "订阅作者", description = "订阅作者更新，当有新的更新时，会进行推送，根据分级会进行选择性包装，你可以用另一个命令设置推送地点", permission = Permission.ADMIN, commands = {"/开启自动撤回", "/关闭自动撤回"})
    @AnyMessageHandler
    @MessageHandlerFilter(types = MsgTypeEnum.text, cmd = "^" + WineFoxBotConfig.COMMAND_PREFIX_REGEX + "(开启自动撤回|关闭自动撤回)" + "$")
    public void toggleAutoRevoke(Bot bot, AnyMessageEvent event) {

    }
}