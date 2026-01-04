package com.github.winefoxbot.plugins.pixiv.service;

import com.github.winefoxbot.plugins.pixiv.model.dto.common.PixivArtworkInfo;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public interface PixivService {

    /** 输入可能是 URL 或 PID，判断是否为 Pixiv URL */
    boolean isPixivURL(String input);

    /** 从 URL 或 PID 文本里提取 PID；若本身就是纯数字 PID 直接返回 */
    String extractPID(String input);

    /** 通过访问作品页判断 PID 是否存在（404 视为无效） */
    boolean isValidPixivPID(String pid) throws IOException;

    /**
     * 获取 Pixiv 作品详情信息
     */
    PixivArtworkInfo getPixivArtworkInfo(String pid) throws IOException;


    /**
     * 拉取作品的媒体文件（自动处理静态多页与 Ugoira -> GIF）；
     * 返回已经落地到磁盘的文件列表（可能是多张图片或一个 GIF）
     */
    CompletableFuture<List<File>> fetchImages(String pid) throws Exception;
}
