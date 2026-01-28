package com.github.winefoxbot.plugins.pixiv.service;

import com.github.winefoxbot.plugins.pixiv.model.dto.common.PixivArtworkInfo;

import java.io.File;
import java.util.List;

/**
 * @author FlanChan (badapple495@outlook.com)
 * @since 2026-01-04-20:47
 */
public interface PixivArtworkService {
    void sendArtwork(PixivArtworkInfo pixivArtworkInfo, List<File> files, String additionalText);
}
