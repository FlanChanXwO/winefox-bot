package com.github.winefoxbot.plugins.bittorrent.service;

import com.github.winefoxbot.plugins.bittorrent.model.dto.BitTorrentSearchResult;

public interface BitTorrentService {


    /**
     * 搜索磁力链接
     *
     * @param keyword
     * @param page
     * @return
     */
    BitTorrentSearchResult search(String keyword, int page);
}
