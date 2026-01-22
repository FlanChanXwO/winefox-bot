package com.github.winefoxbot.plugins.setu.service;

import com.github.winefoxbot.plugins.setu.model.dto.SetuProviderRequest;

import java.util.List;

/**
 * 图片提供者接口
 * 规定了获取涩图API的统一行为
 */
public interface SetuImageProvider {

    /**
     * 获取图片 URL 列表
     */
    List<String> fetchImages(SetuProviderRequest request);
}
