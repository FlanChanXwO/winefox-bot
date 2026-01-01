package com.github.winefoxbot.service.pixiv;

import com.github.winefoxbot.model.dto.pixiv.PixivSearchParams;
import com.github.winefoxbot.model.dto.pixiv.PixivSearchResult;

/**
 * @author FlanChan (badapple495@outlook.com)
 * @since 2025-12-31-17:09
 */


/**
 * Pixiv 搜索服务接口
 */
public interface PixivSearchService {

    /**
     * 根据指定参数在 Pixiv 上搜索作品
     *
     * @param params 搜索参数，包括标签、页码、是否R18等
     * @return 搜索结果，包含截图、作品数据和分页信息
     */
    PixivSearchResult search(PixivSearchParams params);
}

