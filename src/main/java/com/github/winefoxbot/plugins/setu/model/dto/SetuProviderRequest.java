package com.github.winefoxbot.plugins.setu.model.dto;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 统一的图片获取请求对象
 * @param keywords 关键词/标签
 * @param num 数量
 * @param r18 是否R18
 * @param extraParams 额外参数（用于承载 API 特有的参数，如 size, proxy, aspectRatio 等）
 */
public record SetuProviderRequest(
        List<String> keywords,
        int num,
        boolean r18,
        Map<String, Object> extraParams
) {
    // 提供一个简化的构造器
    public SetuProviderRequest(List<String> keywords, int num, boolean r18) {
        this(keywords, num, r18, Collections.emptyMap());
    }
}
