package com.github.winefoxbot.plugins.pixiv.service;

import com.github.winefoxbot.plugins.pixiv.model.dto.common.PixivArtworkInfo;
import com.mikuac.shiro.core.Bot;
import com.mikuac.shiro.dto.event.message.AnyMessageEvent;

import java.io.File;
import java.util.List;

/**
 * @author FlanChan (badapple495@outlook.com)
 * @since 2026-01-04-20:47
 */
public interface PixivArtworkService {
    void sendArtwork(Bot bot, AnyMessageEvent event, PixivArtworkInfo pixivArtworkInfo, List<File> files, String additionalText);

    // In: PixivArtworkService.java

    /**
     * 主动向指定用户发送Pixiv作品（不依赖事件）。
     *
     * @param bot              Bot实例
     * @param userId           接收者QQ号
     * @param pixivArtworkInfo 作品详情
     * @param files            作品图片文件列表
     * @param additionalText   可选的附加文本
     */
    void sendArtworkToUser(Bot bot, Long userId, PixivArtworkInfo pixivArtworkInfo, List<File> files, String additionalText);

    /**
     * 主动向指定群聊发送Pixiv作品（不依赖事件）。
     *
     * @param bot              Bot实例
     * @param groupId          接收群的群号
     * @param pixivArtworkInfo 作品详情
     * @param files            作品图片文件列表
     * @param additionalText   可选的附加文本
     */
    void sendArtworkToGroup(Bot bot, Long groupId, PixivArtworkInfo pixivArtworkInfo, List<File> files, String additionalText);

}
