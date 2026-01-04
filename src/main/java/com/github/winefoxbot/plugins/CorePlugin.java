package com.github.winefoxbot.plugins;

import com.github.winefoxbot.annotation.PluginFunction;
import com.github.winefoxbot.config.app.WineFoxBotProperties;
import com.github.winefoxbot.model.dto.core.RestartInfo;
import com.github.winefoxbot.model.dto.github.GitHubRelease;
import com.github.winefoxbot.model.enums.MessageType;
import com.github.winefoxbot.model.enums.Permission;
import com.github.winefoxbot.service.github.GitHubUpdateService;
import com.mikuac.shiro.annotation.AnyMessageHandler;
import com.mikuac.shiro.annotation.MessageHandlerFilter;
import com.mikuac.shiro.annotation.common.Shiro;
import com.mikuac.shiro.core.Bot;
import com.mikuac.shiro.dto.event.message.AnyMessageEvent;
import com.mikuac.shiro.enums.MsgTypeEnum;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import static com.github.winefoxbot.config.app.WineFoxBotConfig.*;

/**
 * @author FlanChan (badapple495@outlook.com)
 * @since 2025-12-08-0:17
 */
@Shiro
@Component
@Slf4j
@RequiredArgsConstructor
public class CorePlugin {

    private final GitHubUpdateService updateService;
    private final WineFoxBotProperties wineFoxBotProperties;

    /**
     * 应用重启
     */
    @PluginFunction(hidden = true, permission = Permission.SUPERADMIN, group = "核心功能", name = "查看当前版本", description = "查看当前版本", commands = {
            COMMAND_PREFIX + "restart" + COMMAND_SUFFIX,
            COMMAND_PREFIX + "重启" + COMMAND_SUFFIX
    })
    @AnyMessageHandler
    @MessageHandlerFilter(types = MsgTypeEnum.text, cmd = COMMAND_PREFIX_REGEX + "(restart|重启)" + COMMAND_SUFFIX_REGEX)
    public void restartApplication(Bot bot, AnyMessageEvent event) {
        // 1. 准备重启信息
        MessageType messageType = MessageType.fromValue(event.getMessageType());
        Long targetId = switch (messageType) {
            case GROUP -> event.getGroupId();
            case PRIVATE -> event.getUserId();
        };
        // 成功消息模板， {duration} 和 {version} 是占位符，将在重启后填充
        String successMsgTemplate = String.format("[CQ:at,qq=%d] 应用重启成功！\n耗时: {duration}\n当前版本: {version}", event.getUserId());

        // 记录当前时间戳
        long startTime = System.currentTimeMillis();

        RestartInfo restartInfo = new RestartInfo(messageType, targetId, successMsgTemplate, startTime);

        // 2. 保存重启信息到文件
        updateService.saveRestartInfo(restartInfo);

        // 3. 发送即将重启的通知
        bot.sendMsg(event, "收到重启指令，正在保存状态并准备重启...", false);
        log.info("接收到来自 {} 的重启指令", event.getUserId());

        // 4. 执行重启
        updateService.restartApplication();
    }


    /**
     * 查看版本
     */
    @PluginFunction(permission = Permission.USER, group = "核心功能", name = "查看当前版本", description = "查看当前版本", commands = {
            COMMAND_PREFIX + "version" + COMMAND_SUFFIX,
            COMMAND_PREFIX + "当前版本" + COMMAND_SUFFIX
    })
    @AnyMessageHandler
    @MessageHandlerFilter(types = MsgTypeEnum.text, cmd = COMMAND_PREFIX_REGEX + "(version|当前版本)" + COMMAND_SUFFIX_REGEX)
    public void checkVersion(Bot bot, AnyMessageEvent event) {
        String msg;
        try {
            GitHubUpdateService.VersionInfo currentVersion = updateService.getCurrentVersionInfo();
            GitHubRelease latestRelease = updateService.fetchLatestRelease();

            String currentVersionStr = String.format("%s (Release ID: %d)", wineFoxBotProperties.getVersion(), currentVersion.releaseId);
            String latestVersionStr = String.format("%s (Release ID: %d)", latestRelease.getTagName(), latestRelease.getId());

            msg = "版本信息：\n" +
                    "当前版本: " + currentVersionStr + "\n" +
                    "最新版本: " + latestVersionStr;

            if (latestRelease.getId() > currentVersion.releaseId) {
                msg += "\n\n检测到新版本！可发送 '更新版本' 命令进行升级。";
            } else {
                msg += "\n\n当前已是最新版本。";
            }
        } catch (Exception e) {
            log.error("检查版本失败", e);
            msg = "获取版本信息失败: " + e.getMessage();
        }
        bot.sendMsg(event, msg, false);
    }

    /**
     * 更新版本
     */
    @Async
    @PluginFunction(permission = Permission.SUPERADMIN, group = "核心功能", name = "版本更新", description = "版本更新", commands = {
            COMMAND_PREFIX + "update" + COMMAND_SUFFIX,
            COMMAND_PREFIX + "更新版本" + COMMAND_SUFFIX
    })
    @AnyMessageHandler
    @MessageHandlerFilter(types = MsgTypeEnum.text, cmd = COMMAND_PREFIX_REGEX + "(update|更新版本)" + COMMAND_SUFFIX_REGEX)
    public void updateVersion(Bot bot, AnyMessageEvent event) {
        try {
            bot.sendMsg(event, "正在检查并执行更新，请稍候...", false);
            updateService.performUpdate(bot,event);
        } catch (Exception e) {
            log.error("更新失败", e);
            bot.sendMsg(event, "更新操作失败: " + e.getMessage(), false);
        }
    }

}
