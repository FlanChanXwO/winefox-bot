package com.github.winefoxbot.plugins.chat.service;

import org.springframework.stereotype.Service;

@Service
public class ImageTokenCalculator {

    private static final int LOW_DETAIL_COST = 85;
    private static final int HIGH_DETAIL_COST_PER_TILE = 170;
    private static final int BASE_COST = 85;

    /**
     * 计算单张图片的 Token
     *
     * @param width  图片宽度
     * @param height 图片高度
     * @param detail 模式: "low" 或 "high" (auto 通常按 high 算)
     */
    public int countImageTokens(int width, int height, String detail) {
        if ("low".equalsIgnoreCase(detail)) {
            return LOW_DETAIL_COST;
        }

        // 处理 High Detail 模式
        // 1. 初始缩放逻辑
        double processedWidth = width;
        double processedHeight = height;

        // 规则：限制在 2048 x 2048 之内
        if (processedWidth > 2048 || processedHeight > 2048) {
            double ratio = processedWidth / processedHeight;
            if (ratio > 1) { // 宽图
                processedWidth = 2048;
                processedHeight = 2048 / ratio;
            } else { // 长图
                processedHeight = 2048;
                processedWidth = 2048 * ratio;
            }
        }

        // 规则：缩放最短边为 768
        if (processedWidth >= processedHeight && processedHeight > 768) {
            processedWidth = (768 / processedHeight) * processedWidth;
            processedHeight = 768;
        } else if (processedHeight > processedWidth && processedWidth > 768) {
            processedHeight = (768 / processedWidth) * processedHeight;
            processedWidth = 768;
        }

        // 2. 计算瓦片 (Tiles)
        // 每个瓦片是 512x512
        int tilesWidth = (int) Math.ceil(processedWidth / 512.0);
        int tilesHeight = (int) Math.ceil(processedHeight / 512.0);

        int totalTiles = tilesWidth * tilesHeight;

        // 3. 应用公式
        return (totalTiles * HIGH_DETAIL_COST_PER_TILE) + BASE_COST;
    }
}
