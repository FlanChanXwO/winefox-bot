package com.github.winefoxbot.core.model.vo.webui.resp;

public record MessageStatisticsResponse(
            long total,         // 消息总数
            long today,         // 今日消息 (00:00 ~ Now)
            long oneDay,    // 一日内 (Now-24h ~ Now) - 对应 UI "一日内"
            long oneWeek,   // 一周内 (Now-7d ~ Now)
            long oneMonth,  // 一月内 (Now-30d ~ Now)
            long oneYear    // 一年内 (Now-365d ~ Now)
    ) {}