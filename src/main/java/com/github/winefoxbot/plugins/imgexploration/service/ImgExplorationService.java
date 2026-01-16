package com.github.winefoxbot.plugins.imgexploration.service;

import com.github.winefoxbot.plugins.imgexploration.model.dto.ExplorationResultDTO;

import java.util.concurrent.CompletableFuture;

/**
 * 搜图服务核心接口
 * 负责协调下载原图、分发给各个策略引擎、聚合结果以及绘图。
 *
 * @author FlanChan (badapple495@outlook.com)
 * @since 2026-01-15-17:20
 */
public interface ImgExplorationService {

    /**
     * 执行搜图任务
     *
     * @param imageUrl 待搜索图片的原始链接
     * @return 异步结果，包含最终拼接好的结果图(byte[])和搜索结果详情列表
     */
    CompletableFuture<ExplorationResultDTO> explore(String imageUrl);

}
