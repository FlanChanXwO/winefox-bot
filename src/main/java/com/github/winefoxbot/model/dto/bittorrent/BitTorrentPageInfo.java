package com.github.winefoxbot.model.dto.bittorrent;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 磁力链分页信息
 * @author FlanChan (badapple495@outlook.com)
 * @since 2025-12-10-23:54
 */
@Data
public class BitTorrentPageInfo {
    /**
     * 页码列表
     */
    public List<Integer> pages = new ArrayList<>();
    /**
     * 是否有下一页
     */
    public boolean hasNextPage = false;
}