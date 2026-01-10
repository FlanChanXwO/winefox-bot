package com.github.winefoxbot.plugins.fortune;

import com.github.winefoxbot.core.annotation.Plugin;
import com.github.winefoxbot.core.annotation.PluginFunction;
import com.github.winefoxbot.core.config.app.WineFoxBotRobotProperties;
import com.github.winefoxbot.core.model.enums.Permission;
import com.github.winefoxbot.core.utils.BotUtils;
import com.github.winefoxbot.plugins.fortune.config.FortuneConfig;
import com.github.winefoxbot.plugins.fortune.service.FortuneDataService;
import com.mikuac.shiro.annotation.AnyMessageHandler;
import com.mikuac.shiro.annotation.MessageHandlerFilter;
import com.mikuac.shiro.annotation.common.Order;
import com.mikuac.shiro.annotation.common.Shiro;
import com.mikuac.shiro.core.Bot;
import com.mikuac.shiro.dto.event.message.AnyMessageEvent;
import com.mikuac.shiro.enums.AtEnum;
import com.mikuac.shiro.enums.MsgTypeEnum;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.Objects;

@Plugin(
        name = "娱乐功能",
        description = "提供娱乐方式",
        permission = Permission.USER,
        iconPath = "icon/娱乐功能.png",
        order = 7
)
@Component
@Shiro
@Slf4j
@RequiredArgsConstructor
public class FortunePlugin {

    private final FortuneDataService fortuneService;
    private final WineFoxBotRobotProperties robotProperties;
    private final FortuneConfig config;

    @Async
    @PluginFunction(
            name = "今日运势",
            description = "抽取今天的运势",
            commands = {"今日运势","/今日运势"}
    )
    @Order(10)
    @AnyMessageHandler
    @MessageHandlerFilter(types = MsgTypeEnum.text, at = AtEnum.NOT_NEED, cmd = "^/?(今日运势|jrys)$")
    public void getFortune(Bot bot, AnyMessageEvent event) {
        log.info("用户 {} 请求今日运势", event.getUserId());
        fortuneService.processFortune(bot, event);
    }

    @PluginFunction(
            name = "刷新今日运势",
            description = "手动刷新自己的今日运势，配置开启前仅管理员可用",
            commands = {"/刷新今日运势"}
    )
    @Order(9)
    @AnyMessageHandler
    @MessageHandlerFilter(types = MsgTypeEnum.text, at = AtEnum.NOT_NEED, cmd = "^/刷新今日运势$")
    public void refreshFortune(Bot bot, AnyMessageEvent event) {
        if (BotUtils.isAdmin(bot, event.getUserId()) ||
             robotProperties.getSuperUsers().stream().anyMatch(user -> Objects.equals(user, event.getUserId())) ||
             config.isAllowRefreshJrys()) {
            fortuneService.refreshFortune(bot, event);
        } else {
            bot.sendMsg(event, "当前配置不允许手动刷新运势。", false);
        }
    }

    @PluginFunction(
            name = "刷新全局今日运势",
            description = "管理员刷新所有人的运势缓存",
            commands = {"/刷新全局今日运势"},
            permission = Permission.ADMIN // 标记为管理员命令
    )
    @Order(8)
    @AnyMessageHandler
    @MessageHandlerFilter(types = MsgTypeEnum.text, at = AtEnum.NOT_NEED, cmd = "^/刷新全局今日运势$")
    public void refreshAllFortune(Bot bot, AnyMessageEvent event) {
        fortuneService.refreshAllFortune(bot, event);
    }

}
