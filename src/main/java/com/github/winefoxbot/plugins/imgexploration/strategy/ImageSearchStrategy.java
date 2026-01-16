package com.github.winefoxbot.plugins.imgexploration.strategy;

import com.github.winefoxbot.plugins.imgexploration.model.dto.SearchResultItemDTO;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * 搜图策略接口
 * @author FlanChan
 */
public interface ImageSearchStrategy {
    
    /**
     * 获取策略名称 (用于日志或排序)
     */
    String getServiceName();

    /**
     * 执行搜索
     * @param imgUrl 图片的 URL (通常是 ImgOps 的桥接 URL)
     * @return 搜索结果列表
     */
    CompletableFuture<List<SearchResultItemDTO>> search(String imgUrl);
}
