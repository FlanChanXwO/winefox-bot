package com.github.winefoxbot.plugins.pixiv;

import com.github.winefoxbot.core.annotation.Limit;
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
import com.github.winefoxbot.plugins.pixiv.utils.PixivUtils;
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
import java.util.regex.Matcher;

import static com.github.winefoxbot.core.config.app.WineFoxBotConfig.COMMAND_PREFIX_REGEX;
import static com.github.winefoxbot.core.config.app.WineFoxBotConfig.COMMAND_SUFFIX_REGEX;


/**
 * @author FlanChan
 */
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


    @Limit(globalPermits = 20, userPermits = 3 , timeInSeconds = 3)
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


    @Async
    @PluginFunction(name = "收藏P站作品",
            description = "收藏单个Pixiv作品，支持PID或链接。用法：收藏 12345678 或 收藏 https://pixiv.net/artworks/...",
            permission = Permission.SUPERADMIN,
            autoGenerateHelp = true,
            commands = {"/收藏"}
    )
    @AnyMessageHandler
    @MessageHandlerFilter(types = MsgTypeEnum.text, cmd = "^/收藏\\s*(.+)$")
    public void addSingleBookmark(Bot bot, AnyMessageEvent event, Matcher matcher) {
        String arg = matcher.group(1).trim();
        // 解析 PID
        String pid = PixivUtils.extractPID(arg);

        if (pid == null) {
            bot.sendMsg(event, "无法从输入中提取有效的 Pixiv 作品 ID。", false);
            return;
        }

        bot.sendMsg(event, "正在收藏作品 ID: " + pid + " ...", false);
        try {
            boolean success = pixivBookmarkService.addBookmark(pid, 0); // 0 为公开
            if (success) {
                bot.sendMsg(event, "✅ 成功收藏作品: " + pid, false);
            } else {
                bot.sendMsg(event, "❌ 收藏失败，请检查日志 (可能是PID无效或Cookie过期)。", false);
            }
        } catch (Exception e) {
            log.error("收藏指令执行异常", e);
            bot.sendMsg(event, "操作发生异常: " + e.getMessage(), false);
        }
    }

    @Async
    @PluginFunction(name = "移除P站收藏",
            description = "移除单个Pixiv作品收藏，支持PID或链接。用法：取消收藏 12345678",
            permission = Permission.SUPERADMIN,
            autoGenerateHelp = true,
            commands = {"/取消收藏", "/移除收藏"}
    )
    @AnyMessageHandler
    @MessageHandlerFilter(types = MsgTypeEnum.text, cmd = "^/(取消|移除)收藏\\s*(.+)$")
    public void removeSingleBookmark(Bot bot, AnyMessageEvent event, Matcher matcher) {
        String arg = matcher.group(2).trim(); // group 1 是 (取消|移除)，group 2 是参数
        // 解析 PID
        String pid = PixivUtils.extractPID(arg);

        if (pid == null) {
            bot.sendMsg(event, "无法从输入中提取有效的 Pixiv 作品 ID。", false);
            return;
        }

        bot.sendMsg(event, "正在移除作品收藏 ID: " + pid + " ...", false);
        try {
            boolean success = pixivBookmarkService.removeBookmark(pid);
            if (success) {
                bot.sendMsg(event, "🗑️ 成功移除收藏: " + pid, false);
            } else {
                bot.sendMsg(event, "❌ 移除失败，可能网络超时或 API 变更。", false);
            }
        } catch (Exception e) {
            log.error("移除收藏指令执行异常", e);
            bot.sendMsg(event, "操作发生异常: " + e.getMessage(), false);
        }
    }


    @Async
    @PluginFunction(name = "爬取画师收藏",
            description = "爬取指定画师的所有作品并加入收藏。用法：爬取收藏 123456",
            permission = Permission.SUPERADMIN, // 必须是超管权限
            autoGenerateHelp = true,
            commands = {"/全部收藏"}
    )
    @AnyMessageHandler
    @MessageHandlerFilter(types = MsgTypeEnum.text, cmd = "^/全部收藏\\s*(.+)$")
    public void crawlUserArtworks(Bot bot, AnyMessageEvent event, Matcher matcher) {
        String arg = matcher.group(1).trim();
        // 解析 uid
        String uid = PixivUtils.extractUID(arg);

        bot.sendMsg(event, "开始解析画师 [" + uid + "] 的作品列表，正在异步执行批量收藏...", false);

        try {
            int count = pixivBookmarkService.crawlUserArtworksToBookmark(uid);
            if (count > 0) {
                bot.sendMsg(event, "已增加 " + count + " 个作品到鼠鼠の收藏。", false);
            } else {
                bot.sendMsg(event, "未找到该画师的作品，或获取列表失败。", false);
            }
        } catch (Exception e) {
            log.error("爬取收藏指令执行异常", e);
            bot.sendMsg(event, "启动任务失败: " + e.getMessage(), false);
        }
    }


    @Async
    @PluginFunction(name = "转移用户收藏",
            description = "将指定用户的公开收藏全部转移到机器人账号。用法：转移收藏 12345 或 用户主页链接",
            permission = Permission.SUPERADMIN,
            autoGenerateHelp = true,
            commands = {"/转移收藏", "/克隆收藏"}
    )
    @AnyMessageHandler
    @MessageHandlerFilter(types = MsgTypeEnum.text, cmd = "^/(转移|克隆)收藏\\s*(.+)$")
    public void transferBookmarks(Bot bot, AnyMessageEvent event, Matcher matcher) {
        String arg = matcher.group(2).trim();
        // 解析 uid
        String targetUserId = PixivUtils.extractUID(arg);

        if (targetUserId == null) {
            bot.sendMsg(event, "无法提取有效的用户 ID。请输入纯数字 ID 或用户主页链接。", false);
            return;
        }

        bot.sendMsg(event, "🔍 正在扫描用户 [" + targetUserId + "] 的公开收藏列表，请稍候...", false);

        try {
            int count = pixivBookmarkService.transferUserBookmarks(targetUserId);
            if (count > 0) {
                bot.sendMsg(event, "📦 转移完成！共转移 " + count + " 个公开收藏。", false);
            } else {
                bot.sendMsg(event, "⚠️ 未找到该用户的公开收藏，可能是用户设置了隐私，或者 ID 错误。", false);
            }
        } catch (Exception e) {
            log.error("转移收藏指令异常", e);
            bot.sendMsg(event, "操作失败: " + e.getMessage(), false);
        }
    }



}
