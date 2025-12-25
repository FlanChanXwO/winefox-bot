package com.github.winefoxbot.service.pixiv;

import com.github.winefoxbot.model.dto.pixiv.PixivDetail;

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
     * 是否允许访问（含 R18 校验与群限制校验）；允许返回 true，不允许返回 false
     */
    boolean isR18Artwork(String pid, Long groupId) throws IOException;

    /**
     * 获取 Pixiv 作品详情信息
     */
    PixivDetail getPixivArtworkDetail(String pid) throws IOException;


    /**
     * 拉取作品的媒体文件（自动处理静态多页与 Ugoira -> GIF）；
     * 返回已经落地到磁盘的文件列表（可能是多张图片或一个 GIF）
     */
    CompletableFuture<List<File>> fetchImages(String pid) throws Exception;

    void addSchedulePush(Long groupId);
}
