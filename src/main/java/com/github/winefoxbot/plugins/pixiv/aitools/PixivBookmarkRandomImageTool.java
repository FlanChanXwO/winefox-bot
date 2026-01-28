package com.github.winefoxbot.plugins.pixiv.aitools;

import com.github.winefoxbot.core.context.BotContext;
import com.github.winefoxbot.core.model.enums.common.MessageType;
import com.github.winefoxbot.core.utils.SendMsgUtil;
import com.github.winefoxbot.plugins.pixiv.model.dto.common.PixivArtworkInfo;
import com.github.winefoxbot.plugins.pixiv.model.entity.PixivBookmark;
import com.github.winefoxbot.plugins.pixiv.service.PixivArtworkService;
import com.github.winefoxbot.plugins.pixiv.service.PixivBookmarkService;
import com.github.winefoxbot.plugins.pixiv.service.PixivService;
import com.mikuac.shiro.core.Bot;
import com.mikuac.shiro.core.BotContainer;
import com.mikuac.shiro.dto.event.message.AnyMessageEvent;
import com.mikuac.shiro.dto.event.message.MessageEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Description;

import java.io.File;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/**
 * AI工具类，用于调用PixivBookmarkPlugin的随机收藏功能
 * 当AI认为用户想要随机看一张P站收藏的图片时，会调用此工具
 *
 * @author FlanChan
 */
@Configuration(proxyBeanMethods = false)
@RequiredArgsConstructor
@Slf4j
public class PixivBookmarkRandomImageTool {

    private final PixivService pixivService;
    private final PixivBookmarkService pixivBookmarkService;
    private final PixivArtworkService artworkService;

    public record PixivBookmarkImageRequest() {}

    public record PixivBookmarkImageResponse(
            @ToolParam(description = "是否调用工具成功：true:成功 false:失败")  Boolean success,
            @ToolParam(description = "错误信息") String message) {}


    @Bean("randomPixivBookmarkTool")
    @Description("""
    获取用户随机收藏的P站作品图片。
    当用户想要随机查看主人收藏的P站作品时，调用此工具。
    该工具会从用户的P站收藏夹中随机选择一件作品，并获取其详细信息和图片文件，然后发送给用户，当你请求成功时，图片已经被发送了。
    该工具不需要其他输入参数。
    """)
    public Function<PixivBookmarkImageRequest,PixivBookmarkImageResponse> randomPixivBookmarkTool() {
        return _ -> {
            log.info("AI调用随机P站收藏工具");
            Bot bot = BotContext.CURRENT_BOT.get();
            AnyMessageEvent event = (AnyMessageEvent) BotContext.CURRENT_MESSAGE_EVENT.get();
            try {
                bot.sendMsg(event, "正在从收藏夹中抽取作品，请稍候...", false);
                // 1. 随机获取一个收藏
                Optional<PixivBookmark> bookmarkOptional = pixivBookmarkService.getRandomBookmark(event.getUserId(),event.getGroupId());
                if (bookmarkOptional.isEmpty()) {
                    bot.sendMsg(event, "收藏夹是空的哦，还没法抽卡呢~", false);
                    return new PixivBookmarkImageResponse(false, "收藏夹为空，无法抽取随机收藏");
                }
                PixivBookmark bookmark = bookmarkOptional.get();
                String pid = bookmark.getId();
                // 2. 获取作品的详细信息
                PixivArtworkInfo pixivArtworkInfo = pixivService.getPixivArtworkInfo(pid);
                // 3. 异步下载图片文件
                List<File> files = pixivService.fetchImages(pid).join();
                // 4. 调用统一的发送服务
                artworkService.sendArtwork(pixivArtworkInfo,files,null);
                return new PixivBookmarkImageResponse(true, "随机收藏发送成功");
            } catch (Exception e) {
                log.error("网络异常，获取随机收藏失败: {}", e.getMessage(), e);
                return new PixivBookmarkImageResponse(false, "获取随机收藏失败: " + e.getMessage());
            }
        };
    }

}
