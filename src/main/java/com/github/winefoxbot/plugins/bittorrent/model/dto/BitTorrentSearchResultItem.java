package com.github.winefoxbot.plugins.bittorrent.model.dto;

import lombok.Data;
import org.springframework.util.unit.DataSize;

import java.time.LocalDateTime;

/**
 * 磁力链搜索结果项
 * @author FlanChan (badapple495@outlook.com)
 * @since 2025-12-10-22:48
 */
@Data
public class BitTorrentSearchResultItem {
    /**
     * 标题
     */
    private String title;
    /**
     * 热度
     */
    private int hot;
    /**
     * 文件大小
     */
    private DataSize size;
    /**
     * 上传日期
     */
    private LocalDateTime date;
    /**
     * 种子信息
     */
    private BitTorrentMagnetInfo magnetInfo;

}