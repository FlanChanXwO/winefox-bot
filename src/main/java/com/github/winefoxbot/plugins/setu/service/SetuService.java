package com.github.winefoxbot.plugins.setu.service;

import com.github.winefoxbot.plugins.setu.model.enums.SetuApiType;
import com.mikuac.shiro.core.Bot;
import com.mikuac.shiro.dto.event.message.AnyMessageEvent;

import java.util.List;
import java.util.Map;

/**
 * @author FlanChan (badapple495@outlook.com)
 * @since 2026-01-09-8:46
 */
public interface SetuService {

    /**
     * 处理来自事件的请求（使用默认 API）
     */
    void handleSetuRequest(int num, List<String> tag);

    /**
     * 核心处理方法
     *
     * @param extraParams 额外参数
     */
    void handleSetuRequest(int num, List<String> tag, Map<String, Object> extraParams);

}
