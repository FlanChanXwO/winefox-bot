package com.github.winefoxbot.model.dto.bittorrent;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 磁力链搜索结果
 * @author FlanChan (badapple495@outlook.com)
 * @since 2025-12-10-22:48
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BitTorrentMagnetInfo {
    /**
     * 种子链接
     */
    private String magnet;
    /**
     * 文件列表
     */
    private List<String> files;

}