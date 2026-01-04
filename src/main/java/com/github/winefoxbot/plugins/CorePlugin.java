package com.github.winefoxbot.plugins;

import com.github.winefoxbot.annotation.Plugin;
import com.github.winefoxbot.annotation.PluginFunction;
import com.github.winefoxbot.config.app.WineFoxBotProperties;
import com.github.winefoxbot.model.dto.core.RestartInfo;
import com.github.winefoxbot.model.dto.github.GitHubRelease;
import com.github.winefoxbot.model.enums.MessageType;
import com.github.winefoxbot.model.enums.Permission;
import com.github.winefoxbot.service.core.HelpImageService;
import com.github.winefoxbot.service.github.GitHubUpdateService;
import com.mikuac.shiro.annotation.AnyMessageHandler;
import com.mikuac.shiro.annotation.MessageHandlerFilter;
import com.mikuac.shiro.annotation.common.Shiro;
import com.mikuac.shiro.common.utils.MsgUtils;
import com.mikuac.shiro.core.Bot;
import com.mikuac.shiro.dto.event.message.AnyMessageEvent;
import com.mikuac.shiro.enums.MsgTypeEnum;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.regex.Matcher;

import static com.github.winefoxbot.config.app.WineFoxBotConfig.*;

/**
 * @author FlanChan (badapple495@outlook.com)
 * @since 2025-12-08-0:17
 */
@Plugin(
        name = "核心功能",
        description = "提供应用重启、版本查看与更新等核心功能",
        permission = Permission.USER,
        iconPath = "icon/core.png",
        order = 1
)
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
    @PluginFunction(
            name = "应用重启",
            description = "保存状态并重启应用",
            commands = {
                    COMMAND_PREFIX + "restart" + COMMAND_SUFFIX,
                    COMMAND_PREFIX + "重启" + COMMAND_SUFFIX
            },
            permission = Permission.SUPERADMIN, // 覆盖插件默认权限
            hidden = true
    )
    @AnyMessageHandler
    @MessageHandlerFilter(types = MsgTypeEnum.text, cmd = COMMAND_PREFIX_REGEX + "(restart|重启)" + COMMAND_SUFFIX_REGEX)
    public void restartApplication(Bot bot, AnyMessageEvent event) {
        // ... 方法体保持不变
        MessageType messageType = MessageType.fromValue(event.getMessageType());
        Long targetId = switch (messageType) {
            case GROUP -> event.getGroupId();
            case PRIVATE -> event.getUserId();
        };
        String successMsgTemplate = String.format("[CQ:at,qq=%d] 应用重启成功！\n耗时: {duration}\n当前版本: {version}", event.getUserId());
        long startTime = System.currentTimeMillis();
        RestartInfo restartInfo = new RestartInfo(messageType, targetId, successMsgTemplate, startTime);
        updateService.saveRestartInfo(restartInfo);
        bot.sendMsg(event, "收到重启指令，正在保存状态并准备重启...", false);
        log.info("接收到来自 {} 的重启指令", event.getUserId());
        updateService.restartApplication();
    }


    /**
     * 查看版本
     */
    @PluginFunction(
            name = "查看当前版本",
            description = "显示当前应用版本和最新的可用版本",
            commands = {
                    COMMAND_PREFIX + "version" + COMMAND_SUFFIX,
                    COMMAND_PREFIX + "当前版本" + COMMAND_SUFFIX
            }
            // 此处未指定permission，将继承 @Plugin 中定义的 Permission.USER
    )
    @AnyMessageHandler
    @MessageHandlerFilter(types = MsgTypeEnum.text, cmd = COMMAND_PREFIX_REGEX + "(version|当前版本)" + COMMAND_SUFFIX_REGEX)
    public void checkVersion(Bot bot, AnyMessageEvent event) {
        // ... 方法体保持不变
        String msg;
        try {
            GitHubUpdateService.VersionInfo currentVersion = updateService.getCurrentVersionInfo();
            GitHubRelease latestRelease = updateService.fetchLatestRelease();
            String currentVersionStr = String.format("%s (Release ID: %d)", wineFoxBotProperties.getApp().getVersion(), currentVersion.releaseId);
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
    @PluginFunction(
            name = "版本更新",
            description = "从GitHub下载并更新到最新版本",
            commands = {
                    COMMAND_PREFIX + "update" + COMMAND_SUFFIX,
                    COMMAND_PREFIX + "更新版本" + COMMAND_SUFFIX
            },
            permission = Permission.SUPERADMIN // 覆盖插件默认权限
    )
    @AnyMessageHandler
    @MessageHandlerFilter(types = MsgTypeEnum.text, cmd = COMMAND_PREFIX_REGEX + "(update|更新版本)" + COMMAND_SUFFIX_REGEX)
    public void updateVersion(Bot bot, AnyMessageEvent event) {
        // ... 方法体保持不变
        try {
            bot.sendMsg(event, "正在检查并执行更新，请稍候...", false);
            updateService.performUpdate(bot,event);
        } catch (Exception e) {
            log.error("更新失败", e);
            bot.sendMsg(event, "更新操作失败: " + e.getMessage(), false);
        }
    }

    private final HelpImageService helpImageService;

    @PluginFunction(
            name = "帮助文档",
            description = "生成并发送帮助图片，展示所有可用功能及其说明。如果在命令1个空格之后加上\"<功能组名>\"可以获取指定功能组的帮助文档图片。", permission = Permission.USER,
            commands = {
                    COMMAND_PREFIX + "help" + COMMAND_SUFFIX,
                    COMMAND_PREFIX +  "h" + COMMAND_SUFFIX,
                    COMMAND_PREFIX + "wf帮助" + COMMAND_SUFFIX,
                    COMMAND_PREFIX + "帮助" + COMMAND_SUFFIX})
    @AnyMessageHandler
    @MessageHandlerFilter(types = MsgTypeEnum.text, cmd = COMMAND_PREFIX_REGEX + "(help|h|wf帮助|帮助)(?:\\s+(.+))?" + COMMAND_SUFFIX_REGEX)
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
