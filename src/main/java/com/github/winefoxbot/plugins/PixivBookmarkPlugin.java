package com.github.winefoxbot.plugins;

import com.github.winefoxbot.annotation.Plugin;
import com.github.winefoxbot.annotation.PluginFunction;
import com.github.winefoxbot.exception.bot.NetworkException;
import com.github.winefoxbot.exception.bot.PluginExecutionException;
import com.github.winefoxbot.model.dto.pixiv.PixivDetail;
import com.github.winefoxbot.model.entity.PixivBookmark;
import com.github.winefoxbot.model.enums.Permission;
import com.github.winefoxbot.service.pixiv.PixivArtworkService;
import com.github.winefoxbot.service.pixiv.PixivBookmarkService;
import com.github.winefoxbot.service.pixiv.PixivService;
import com.github.winefoxbot.service.shiro.ShiroSessionStateService;
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

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static com.github.winefoxbot.config.app.WineFoxBotConfig.COMMAND_PREFIX;
import static com.github.winefoxbot.config.app.WineFoxBotConfig.COMMAND_SUFFIX;


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

    /**
     * 新增的随机抽取收藏功能
     */
    @Async
    @PluginFunction(name = "鼠鼠の收藏",
            description = "从鼠鼠的收藏夹中随机抽取一张作品，发送 \"鼠鼠的收藏\" 命令即可获得~",
            permission = Permission.USER,
            autoGenerateHelp = false,
            commands = {"鼠鼠的收藏"}
    )
    @AnyMessageHandler
    @MessageHandlerFilter(types = MsgTypeEnum.text, cmd = "鼠鼠的收藏")
    public void getRandomBookmark(Bot bot, AnyMessageEvent event) {
        log.info("用户 [{}] 请求随机收藏。", event.getUserId());
        String sessionKey = shiroSessionStateService.getSessionKey(event);
        shiroSessionStateService.enterCommandMode(sessionKey);
        try {
            Optional<PixivBookmark> bookmarkOptional = pixivBookmarkService.getRandomBookmark();

            if (bookmarkOptional.isEmpty()) {
                bot.sendMsg(event, "收藏夹是空的哦，还没法抽卡呢~", false);
                return;
            }

            // 获取到了数据
            PixivBookmark bookmark = bookmarkOptional.get();
            String pid = bookmark.getId();

            bot.sendMsg(event, "正在从收藏夹中抽取作品，请稍候...", false);

            try {
                // 1. 获取作品的详细信息
                PixivDetail pixivDetail = pixivService.getPixivArtworkDetail(pid);

                // 2. 异步下载图片文件
                List<File> files = pixivService.fetchImages(pid).join();

                // 3. 调用统一的发送服务，传入 null 表示没有额外的提示文本
                pixivArtworkService.sendArtwork(bot, event, pixivDetail, files, null);
            } catch (Exception e) {
                throw new NetworkException(bot, event, "获取 Pixiv 收藏作品失败: " + e.getMessage(), e);
            }
        } finally {
            shiroSessionStateService.exitCommandMode(sessionKey);
        }

    }

}
