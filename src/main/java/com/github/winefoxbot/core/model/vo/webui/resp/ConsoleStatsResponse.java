package com.github.winefoxbot.core.model.vo.webui.resp;

import java.io.Serializable;
import java.util.List;

/**
 * 仪表盘统计数据响应
 *
 * @param trend      趋势图数据
 */
public record ConsoleStatsResponse(
        TrendChartData trend
) implements Serializable {

    /**
     * 趋势图数据
     */
    public record TrendChartData(
            List<String> dates,      // X轴：日期 (MM-dd)
            List<Long> msgCounts,    // Y轴1：消息统计
            List<Long> callCounts    // Y轴2：调用统计
    ) implements Serializable {
    }
}
