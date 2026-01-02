package com.github.winefoxbot.plugins;

import com.github.winefoxbot.annotation.PluginFunction;
import com.github.winefoxbot.config.app.WineFoxBotProperties;
import com.github.winefoxbot.model.dto.github.GitHubRelease;
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
import org.springframework.stereotype.Component;

import java.io.File;
import java.lang.management.ManagementFactory;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;

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
public class CorePlugin {

    private final GitHubUpdateService updateService;
    private final WineFoxBotProperties wineFoxBotProperties;

    /**
     * 应用重启
     */
    @PluginFunction(hidden = true, permission = Permission.ADMIN, group = "核心功能", name = "查看当前版本", description = "查看当前版本" ,commands = {
            COMMAND_PREFIX_REGEX + "restart" + COMMAND_PREFIX_REGEX,
            COMMAND_PREFIX_REGEX + "重启" + COMMAND_SUFFIX_REGEX
    })
    @AnyMessageHandler
    @MessageHandlerFilter(types = MsgTypeEnum.text, cmd = COMMAND_PREFIX_REGEX + "(restart|重启)" + COMMAND_SUFFIX_REGEX)
    public void restartApplication(Bot bot, AnyMessageEvent event) {
        bot.sendMsg(event, "收到重启指令，应用将在稍后重启...", false);
        log.info("接收到来自 {} 的重启指令", event.getUserId());
        updateService.restartApplication();
    }


    /**
     * 查看版本
     */
    @PluginFunction( permission = Permission.USER, group = "核心功能", name = "查看当前版本", description = "查看当前版本" ,commands = {
            COMMAND_PREFIX_REGEX + "version" + COMMAND_PREFIX_REGEX,
            COMMAND_PREFIX_REGEX + "当前版本" + COMMAND_SUFFIX_REGEX
    })
    @AnyMessageHandler
    @MessageHandlerFilter(types = MsgTypeEnum.text, cmd = COMMAND_PREFIX_REGEX + "(version|当前版本)" + COMMAND_SUFFIX_REGEX)
    public void checkVersion(Bot bot, AnyMessageEvent event) {
        String msg;
        try {
            GitHubUpdateService.VersionInfo currentVersion = updateService.getCurrentVersionInfo();
            GitHubRelease latestRelease = updateService.fetchLatestRelease();

            String currentVersionStr = String.format("v%s (Release ID: %d)", wineFoxBotProperties.getVersion(), currentVersion.releaseId);
            String latestVersionStr = String.format("%s (Release ID: %d)", latestRelease.getTagName(), latestRelease.getId());

            msg = "版本信息：\n" +
                    "当前版本: " + currentVersionStr + "\n" +
                    "最新版本: " + latestVersionStr;

            if (latestRelease.getId() > currentVersion.releaseId) {
                msg += "\n\n检测到新版本！管理员可发送 '更新版本' 进行升级。";
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
    @PluginFunction(permission = Permission.ADMIN, group = "核心功能", name = "版本更新", description = "版本更新" ,commands = {
            COMMAND_PREFIX_REGEX + "update" + COMMAND_PREFIX_REGEX,
            COMMAND_PREFIX_REGEX + "更新版本" + COMMAND_SUFFIX_REGEX
    })
    @AnyMessageHandler
    @MessageHandlerFilter(types = MsgTypeEnum.text, cmd = COMMAND_PREFIX_REGEX + "(update|更新版本)" + COMMAND_SUFFIX_REGEX)
    public void updateVersion(Bot bot, AnyMessageEvent event) {
        try {
            bot.sendMsg(event, "正在检查并执行更新，请稍候...", false);
            updateService.performUpdate();
            // performUpdate 成功后会自动重启，这里不需要再发消息
        } catch (Exception e) {
            log.error("更新失败", e);
            bot.sendMsg(event, "更新操作失败: " + e.getMessage(), false);
        }
    }

    /**
     * 获取当前运行的JAR文件路径
     */
    private String getCurrentJarPath() throws URISyntaxException {
        return new File(CorePlugin.class.getProtectionDomain().getCodeSource().getLocation().toURI()).getPath();
    }
}
