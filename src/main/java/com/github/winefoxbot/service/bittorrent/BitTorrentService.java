package com.github.winefoxbot.service.bittorrent;

import com.github.winefoxbot.model.dto.bittorrent.BitTorrentSearchResult;

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
