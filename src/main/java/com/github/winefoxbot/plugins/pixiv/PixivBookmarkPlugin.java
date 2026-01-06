package com.github.winefoxbot.plugins.pixiv;

import com.github.winefoxbot.core.annotation.Plugin;
import com.github.winefoxbot.core.annotation.PluginFunction;
import com.github.winefoxbot.core.exception.bot.PluginExecutionException;
import com.github.winefoxbot.core.model.enums.Permission;
import com.github.winefoxbot.core.service.shiro.ShiroSessionStateService;
import com.github.winefoxbot.plugins.pixiv.model.dto.common.PixivArtworkInfo;
import com.github.winefoxbot.plugins.pixiv.model.entity.PixivBookmark;
import com.github.winefoxbot.plugins.pixiv.service.PixivArtworkService;
import com.github.winefoxbot.plugins.pixiv.service.PixivBookmarkService;
import com.github.winefoxbot.plugins.pixiv.service.PixivService;
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

import static com.github.winefoxbot.core.config.app.WineFoxBotConfig.COMMAND_PREFIX_REGEX;
import static com.github.winefoxbot.core.config.app.WineFoxBotConfig.COMMAND_SUFFIX_REGEX;


@Plugin(name = "Pixiv",
        description = "提供 Pixiv 图片获取与排行榜订阅等功能",
        permission = Permission.USER,
        iconPath = "icon/pixiv.png",
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
            autoGenerateHelp = false,
            commands = { "/同步P站收藏" ,"/同步p站收藏"}
    )
    @AnyMessageHandler
    @MessageHandlerFilter(types = MsgTypeEnum.text, cmd = COMMAND_PREFIX_REGEX + "同步((p|P)(ixiv|站))收藏" + COMMAND_SUFFIX_REGEX)
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
        String sessionKey = shiroSessionStateService.getSessionKey(event);
        shiroSessionStateService.enterCommandMode(sessionKey);
        Long userId = event.getUserId();
        Long groupId = event.getGroupId();
        try {
            bot.sendMsg(event, "正在从收藏夹中抽取作品，请稍候...", false);
            // 1. 随机获取一个收藏
            Optional<PixivBookmark> bookmarkOptional = pixivBookmarkService.getRandomBookmark(userId,groupId);
            if (bookmarkOptional.isEmpty()) {
                bot.sendMsg(event, "收藏夹是空的哦，还没法抽卡呢~", false);
                return; // 收藏夹为空，直接退出
            }
            PixivBookmark bookmark = bookmarkOptional.get();
            String pid = bookmark.getId();
            // 2. 获取作品的详细信息
            PixivArtworkInfo pixivArtworkInfo = pixivService.getPixivArtworkInfo(pid);
            // 3. 异步下载图片文件
            List<File> files = pixivService.fetchImages(pid).join();
            // 4. 调用统一的发送服务
            pixivArtworkService.sendArtwork(bot, event, pixivArtworkInfo, files, null);
            log.info("用户 [{}] 的随机收藏发送完成，作品ID: {}。", event.getUserId(), pid);
        } catch (Exception e) {
            log.error("网络异常，获取随机收藏失败: {}", e.getMessage(), e);
            throw new PluginExecutionException(bot, event, "获取随机收藏失败", e);
        } finally {
            shiroSessionStateService.exitCommandMode(sessionKey);
        }
    }

}
