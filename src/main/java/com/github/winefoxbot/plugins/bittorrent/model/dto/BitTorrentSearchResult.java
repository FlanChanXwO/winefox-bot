package com.github.winefoxbot.plugins.bittorrent.model.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 磁力链搜索结果
 * @author FlanChan (badapple495@outlook.com)
 * @since 2025-12-10-22:48
 */
@Data
public class BitTorrentSearchResult {
    /**
     * 搜索项目列表
     */
    private List<BitTorrentSearchResultItem> items = new ArrayList<>();

    /**
     * 分页信息
     */
    private BitTorrentPageInfo pageInfo;
}