package com.github.winefoxbot.plugins.pixiv.service;

import com.github.winefoxbot.plugins.pixiv.model.dto.common.PixivArtworkInfo;
import com.mikuac.shiro.core.Bot;
import com.mikuac.shiro.dto.event.message.AnyMessageEvent;
import org.springframework.scheduling.annotation.Async;

import java.io.File;
import java.util.List;

/**
 * @author FlanChan (badapple495@outlook.com)
 * @since 2026-01-04-20:47
 */
public interface PixivArtworkService {
    @Async("taskExecutor") // 使用一个公共的线程池
    void sendArtwork(Bot bot, AnyMessageEvent event, PixivArtworkInfo pixivArtworkInfo, List<File> files, String additionalText);
}
