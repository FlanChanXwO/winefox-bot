package com.github.winefoxbot.core.plugins;

import com.github.winefoxbot.core.annotation.Plugin;
import com.github.winefoxbot.core.annotation.PluginFunction;
import com.github.winefoxbot.core.config.app.WineFoxBotProperties;
import com.github.winefoxbot.core.exception.bot.PluginExecutionException;
import com.github.winefoxbot.core.model.dto.GitHubRelease;
import com.github.winefoxbot.core.model.enums.Permission;
import com.github.winefoxbot.core.service.helpdoc.HelpImageService;
import com.github.winefoxbot.core.service.status.StatusImageService;
import com.github.winefoxbot.core.service.update.GitHubUpdateService;
import com.mikuac.shiro.annotation.AnyMessageHandler;
import com.mikuac.shiro.annotation.MessageHandlerFilter;
import com.mikuac.shiro.annotation.common.Shiro;
import com.mikuac.shiro.common.utils.MsgUtils;
import com.mikuac.shiro.core.Bot;
import com.mikuac.shiro.dto.event.message.AnyMessageEvent;
import com.mikuac.shiro.enums.MsgTypeEnum;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.regex.Matcher;

import static com.github.winefoxbot.core.config.app.WineFoxBotConfig.*;

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
    private final HelpImageService helpImageService;
    private final StatusImageService statusImageService;

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
        bot.sendMsg(event, "收到重启指令，正在保存状态并准备重启...", false);
        log.info("接收到来自 {} 的重启指令", event.getUserId());
        updateService.restartApplication(event);
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
        String msg;
        try {
            GitHubUpdateService.VersionInfo currentVersion = updateService.getCurrentVersionInfo();
            GitHubRelease latestRelease = updateService.fetchLatestRelease();
            msg = "版本信息：\n" +
                    "当前版本: " + 'v' +  wineFoxBotProperties.getApp().getVersion() + "\n" +
                    "最新版本: " + latestRelease.getTagName();
            if (latestRelease.getId() > currentVersion.releaseId) {
                msg += "\n\n检测到新版本！可发送 '/更新版本' 命令进行升级。";
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
     * 查看更新日志
     */
    @PluginFunction(
            name = "更新日志",
            description = "查看最新版本的更新详情、修复内容及发布时间",
            commands = {
                    COMMAND_PREFIX + "changes" + COMMAND_SUFFIX,
                    COMMAND_PREFIX + "changelog" + COMMAND_SUFFIX,
                    COMMAND_PREFIX + "更新日志" + COMMAND_SUFFIX,
                    COMMAND_PREFIX + "更新内容" + COMMAND_SUFFIX
            }

    )
    @AnyMessageHandler
    @MessageHandlerFilter(types = MsgTypeEnum.text, cmd = COMMAND_PREFIX_REGEX + "(changes|changelog|更新日志|更新内容)" + COMMAND_SUFFIX_REGEX)
    public void checkReleaseNotes(Bot bot, AnyMessageEvent event) {
        try {
            // 1. 获取最新 Release 信息
            GitHubRelease release = updateService.fetchLatestRelease();

            // 2. 处理时间格式 (GitHub 返回的是 ISO 8601，如 2025-01-14T12:00:00Z)
            String publishTime = "未知时间";
            if (release.getPublishedAt() != null) {
                try {
                    // 使用 ZonedDateTime 解析 ISO 时间并转换为更易读的格式
                    var zdt = ZonedDateTime.parse(release.getPublishedAt());
                    publishTime = zdt.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
                } catch (Exception ignored) {
                    publishTime = release.getPublishedAt(); // 解析失败则原样显示
                }
            }
            // 3. 处理日志内容 (如果是 Markdown，可以在这里做简单的清洗，或者直接发送)
            String msg = buildUpdateIntroduction(release, publishTime);
            bot.sendMsg(event, msg, false);
        } catch (Exception e) {
            log.error("获取更新日志失败", e);
            bot.sendMsg(event, "获取更新日志失败: " + e.getMessage(), false);
        }
    }

    private static @NonNull String buildUpdateIntroduction(GitHubRelease release, String publishTime) {
        String body = release.getBody();
        if (body == null || body.isBlank()) {
            body = "该版本暂无详细说明。";
        }

        // 4. 构建消息
        return """
                📦 最新版本信息
                ━━━━━━━━━━━━━━
                🔖 版本号： %s
                📅 发布于： %s
                
                📝 更新内容：
                %s
                
                (发送 '/更新版本' 可执行更新)
                """.formatted(
                release.getTagName(),
                publishTime,
                body
        );
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
        try {
            bot.sendMsg(event, "正在检查并执行更新，请稍候...", false);
            updateService.performUpdate(bot,event);
        } catch (Exception e) {
            log.error("更新失败", e);
            bot.sendMsg(event, "更新操作失败: " + e.getMessage(), false);
        }
    }


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
    public void fetchHelpImage(Bot bot, AnyMessageEvent event, Matcher matcher) {
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


    @PluginFunction(
            name = "状态查询",
            description = "查询酒狐的状态", permission = Permission.USER,
            commands = {
                    COMMAND_PREFIX + "status" + COMMAND_SUFFIX,
                    COMMAND_PREFIX +  "状态" + COMMAND_SUFFIX
            })
    @AnyMessageHandler
    @MessageHandlerFilter(types = MsgTypeEnum.text, cmd = COMMAND_PREFIX_REGEX + "(status|状态)(?:\\s+(.+))?" + COMMAND_SUFFIX_REGEX)
    public void fetchBotStatus(Bot bot, AnyMessageEvent event, Matcher matcher) {
        try {
            byte[] bytes = statusImageService.generateStatusImage();
            bot.sendMsg(event, MsgUtils.builder().img(bytes).build(), false);
        } catch (IOException | InterruptedException e) {
            throw new PluginExecutionException(bot,event, "状态丢失了...", e);
        }
    }
}
