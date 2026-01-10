package com.github.winefoxbot.plugins.pixiv.aitools;

import com.github.winefoxbot.core.model.enums.MessageType;
import com.github.winefoxbot.core.utils.SendMsgUtil;
import com.github.winefoxbot.plugins.pixiv.model.dto.common.PixivArtworkInfo;
import com.github.winefoxbot.plugins.pixiv.model.entity.PixivBookmark;
import com.github.winefoxbot.plugins.pixiv.service.PixivArtworkService;
import com.github.winefoxbot.plugins.pixiv.service.PixivBookmarkService;
import com.github.winefoxbot.plugins.pixiv.service.PixivService;
import com.mikuac.shiro.core.Bot;
import com.mikuac.shiro.core.BotContainer;
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
    private final BotContainer botContainer;
    private final PixivArtworkService artworkService;

    public record PixivBookmarkImageRequest(
            @ToolParam(required = false, description = "调用该工具所需的uid，需要从json消息的uid字段中获取")
            Long userId,
            @ToolParam(required = false, description = "调用该工具所需的session_id，需要从json消息的session_id字段中获取")
            Long sessionId,
            @ToolParam(required = true, description = "调用该工具所需的message_type，需要从json消息的message_type字段中获取,该参数必须为小写")
            String messageType
    ) {}

    public record PixivBookmarkImageResponse(
            @ToolParam(description = "是否调用工具成功：true:成功 false:失败")  Boolean success,
            @ToolParam(description = "错误信息") String message) {}


    @Bean("randomPixivBookmarkTool")
    @Description("随机获取一张P站（Pixiv）收藏的画作。当用户想看涩图、色图或随机图片时使用此功能。")
    public Function<PixivBookmarkImageRequest,PixivBookmarkImageResponse> randomPixivBookmarkTool() {
        return pixivBookmarkImageRequest -> {
            Optional<Bot> botOptional = botContainer.robots.values().stream().findFirst();
            if (botOptional.isEmpty()) {
                return new PixivBookmarkImageResponse(false, "没有可用的机器人实例");
            }


            Bot bot = botOptional.get();
            try {
                String tipMsg = "正在为你获取随机收藏的P站作品...";
                sendTip(pixivBookmarkImageRequest, bot, tipMsg);
                // 1. 随机获取一个收藏
                Optional<PixivBookmark> bookmarkOptional = pixivBookmarkService.getRandomBookmark(pixivBookmarkImageRequest.userId(), pixivBookmarkImageRequest.sessionId);
                if (bookmarkOptional.isEmpty()) {
                    tipMsg = "你的收藏夹为空，无法获取随机收藏。";
                    sendTip(pixivBookmarkImageRequest, bot, tipMsg);
                    return new PixivBookmarkImageResponse(false, "收藏夹为空");
                }
                PixivBookmark bookmark = bookmarkOptional.get();
                String pid = bookmark.getId();
                // 2. 获取作品的详细信息
                PixivArtworkInfo pixivArtworkInfo = pixivService.getPixivArtworkInfo(pid);
                // 3. 异步下载图片文件
                List<File> files = pixivService.fetchImages(pid).join();
                // 4. 调用统一的发送服务
                sendImage(pixivBookmarkImageRequest, bot, pixivArtworkInfo, files, pid);
                return new PixivBookmarkImageResponse(true, "随机收藏发送成功");
            } catch (Exception e) {
                log.error("网络异常，获取随机收藏失败: {}", e.getMessage(), e);
                return new PixivBookmarkImageResponse(false, "获取随机收藏失败: " + e.getMessage());
            }
        };
    }

    private void sendImage(PixivBookmarkImageRequest pixivBookmarkImageRequest, Bot bot, PixivArtworkInfo pixivArtworkInfo, List<File> files, String pid) {
        MessageType messageType = MessageType.fromValue(pixivBookmarkImageRequest.messageType.toLowerCase());
        if (messageType.equals(MessageType.GROUP)) {
            artworkService.sendArtworkToGroup(bot, pixivBookmarkImageRequest.sessionId, pixivArtworkInfo, files, null);
            log.info("群 [{}] 的随机收藏发送完成，作品ID: {}。", pixivBookmarkImageRequest.sessionId, pid);
        } else {
            artworkService.sendArtworkToUser(bot, pixivBookmarkImageRequest.sessionId, pixivArtworkInfo, files, null);
            log.info("用户 [{}] 的随机收藏发送完成，作品ID: {}。", pixivBookmarkImageRequest.sessionId, pid);
        }
    }

    private static void sendTip(PixivBookmarkImageRequest pixivBookmarkImageRequest, Bot bot, String tipMsg) {
        MessageType messageType = MessageType.fromValue(pixivBookmarkImageRequest.messageType.toLowerCase());
        if (messageType.equals(MessageType.GROUP)) {
            SendMsgUtil.sendGroupMsg(bot, pixivBookmarkImageRequest.sessionId, tipMsg,false);
        } else {
            SendMsgUtil.sendPrivateMsg(bot, pixivBookmarkImageRequest.sessionId, tipMsg, false);
        }
    }
}
