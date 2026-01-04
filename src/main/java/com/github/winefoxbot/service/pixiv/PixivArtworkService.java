package com.github.winefoxbot.service.pixiv;

import com.github.winefoxbot.model.dto.pixiv.PixivDetail;
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
    void sendArtwork(Bot bot, AnyMessageEvent event, PixivDetail pixivDetail, List<File> files, String additionalText);
}
