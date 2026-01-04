package com.github.winefoxbot.plugins.pixiv;

import com.github.winefoxbot.core.annotation.Plugin;
import com.github.winefoxbot.core.annotation.PluginFunction;
import com.github.winefoxbot.core.exception.bot.NetworkException;
import com.github.winefoxbot.core.exception.bot.PluginExecutionException;
import com.github.winefoxbot.plugins.pixiv.model.dto.common.PixivArtworkInfo;
import com.github.winefoxbot.plugins.pixiv.model.entity.PixivBookmark;
import com.github.winefoxbot.core.model.enums.Permission;
import com.github.winefoxbot.plugins.pixiv.service.PixivArtworkService;
import com.github.winefoxbot.plugins.pixiv.service.PixivBookmarkService;
import com.github.winefoxbot.plugins.pixiv.service.PixivService;
import com.github.winefoxbot.core.service.shiro.ShiroSessionStateService;
import com.mikuac.shiro.annotation.AnyMessageHandler;
import com.mikuac.shiro.annotation.MessageHandlerFilter;
import com.mikuac.shiro.annotation.common.Order;
import com.mikuac.shiro.annotation.common.Shiro;
import com.mikuac.shiro.core.Bot;
import com.mikuac.shiro.dto.event.message.AnyMessageEvent;
import com.mikuac.shiro.enums.MsgTypeEnum;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.io.File;
import java.util.List;
import java.util.Optional;

import static com.github.winefoxbot.core.config.app.WineFoxBotConfig.COMMAND_PREFIX;
import static com.github.winefoxbot.core.config.app.WineFoxBotConfig.COMMAND_SUFFIX;


@Plugin(name = "Pixiv",
        description = "提供 Pixiv 图片获取与排行榜订阅功能",
        permission = Permission.USER,
        iconPath = "icon/pixiv.ico",
        order = 13
)
@Component
@Slf4j
@Shiro
@RequiredArgsConstructor
public class PixivBookmarkPlugin {
    private final PixivService pixivService;
    private final PixivArtworkService pixivArtworkService;
    private final PixivBookmarkService pixivBookmarkService;
    private final ShiroSessionStateService shiroSessionStateService;
    /**
     * 定义最大重试次数为类的成员常量
     */
    private static final int MAX_RETRIES = 3;


    @Async
    @PluginFunction(name = "同步 Pixiv 收藏夹",
            description = "手动同步 Pixiv 收藏夹中的作品",
            permission = Permission.SUPERADMIN,
            autoGenerateHelp = true
    )
    @AnyMessageHandler
    @MessageHandlerFilter(types = MsgTypeEnum.text, cmd = COMMAND_PREFIX + "同步((p|P)(ixiv|站))收藏" + COMMAND_SUFFIX)
    public void syncPixivBookmarks(Bot bot, AnyMessageEvent event) {
        log.info("手动触发 Pixiv 收藏夹同步...");
        bot.sendMsg(event, "正在同步 Pixiv 收藏夹，请稍候...", false);
        try {
            pixivBookmarkService.syncBookmarks();
            bot.sendMsg(event, "Pixiv 收藏夹同步完成！", false);
        } catch (Exception e) {
            throw new PluginExecutionException(bot, event, "同步 Pixiv 收藏夹失败: " + e.getMessage(), e);
        }
    }


    @Async
    @PluginFunction(name = "鼠鼠の收藏",
            description = "从鼠鼠的收藏夹中随机抽取一张作品，发送 \"鼠鼠的收藏\" 命令即可获得~",
            permission = Permission.USER,
            autoGenerateHelp = false,
            commands = {"鼠鼠的收藏"}
    )
    @Order(10)
    @AnyMessageHandler
    @MessageHandlerFilter(types = MsgTypeEnum.text, cmd = "^鼠鼠的收藏$")
    public void getRandomBookmark(Bot bot, AnyMessageEvent event) {
        log.info("用户 [{}] 请求随机收藏。", event.getUserId());
        String sessionKey = shiroSessionStateService.getSessionKey(event);
        shiroSessionStateService.enterCommandMode(sessionKey);

        try {
            bot.sendMsg(event, "正在从收藏夹中抽取作品，请稍候...", false);

            // 循环实现重试逻辑，但这次是重试“抽卡”这个动作
            for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
                // 1. 随机获取一个收藏
                Optional<PixivBookmark> bookmarkOptional = pixivBookmarkService.getRandomBookmark();
                if (bookmarkOptional.isEmpty()) {
                    bot.sendMsg(event, "收藏夹是空的哦，还没法抽卡呢~", false);
                    return; // 收藏夹为空，直接退出
                }

                PixivBookmark bookmark = bookmarkOptional.get();
                String pid = bookmark.getId();
                log.info("第 {} 次尝试，抽中PID: {}", attempt, pid);

                try {
                    // 2. 获取作品的详细信息
                    PixivArtworkInfo pixivArtworkInfo = pixivService.getPixivArtworkInfo(pid);

                    // 3. 异步下载图片文件
                    List<File> files = pixivService.fetchImages(pid).join();

                    // 4. 调用统一的发送服务
                    pixivArtworkService.sendArtwork(bot, event, pixivArtworkInfo, files, null);

                    // 5. 如果成功，任务完成，直接返回
                    return;

                } catch (Exception e) {
                    // 捕获针对单个PID的异常
                    log.error("处理 PID: {} 失败 (尝试次数: {}/{})。错误: {}", pid, attempt, MAX_RETRIES, e.getMessage());

                    if (attempt >= MAX_RETRIES) {
                        // 如果已经达到最大重试次数，则发送最终的失败消息
                        log.error("已达最大重试次数，获取 Pixiv 收藏作品最终失败。", e);
                        throw new NetworkException(bot, event, "获取 Pixiv 收藏作品失败，请稍后再试。", e);
                    }
                    // 如果未达到最大重试次数，循环将继续，抽取下一个随机作品
                }
            }
        } finally {
            shiroSessionStateService.exitCommandMode(sessionKey);
        }
    }

}
