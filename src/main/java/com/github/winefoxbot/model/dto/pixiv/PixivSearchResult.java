package com.github.winefoxbot.model.dto.pixiv;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * @author FlanChan (badapple495@outlook.com)
 * @since 2025-12-31-17:12
 */
@Data
@Builder
public class PixivSearchResult {
    /**
     * 包含作品列表区域的截图PNG数据
     */
    private byte[] screenshot;

    /**
     * 从页面提取的作品信息列表
     */
    private List<ArtworkData> artworks;

    /**
     * 当前页码
     */
    private int currentPage;

    /**
     * 总页数
     */
    private int totalPages;

    /**
     * 总作品数
     */
    private long totalArtworks;

    /**
     * 本次是否为R18搜索
     */
    private boolean r18;

    /**
     * 内部类，用于存储单个作品信息
     */
    @Data
    @Builder
    public static class ArtworkData {
        /**
         * 作品pid
         */
        private String pid;
        /**
         * 作者uid
         */
        private String authorId;
    }

}