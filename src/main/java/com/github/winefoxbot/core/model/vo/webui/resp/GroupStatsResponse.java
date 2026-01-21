package com.github.winefoxbot.core.model.vo.webui.resp;

import java.util.List;

/**
 * 仪表盘统计数据响应
 *
 * @param activeGroups 活跃群组 Top5
 */
public record GroupStatsResponse(
        List<GroupActivity> activeGroups
) {

    /**
     * 群组活跃度数据 (适用于柱状图)
     */
    public record GroupActivity(
            Long groupId,   // 群号 (前端可能用到头像等)
            String name,    // 群名 (Y轴/X轴 Label)
            Long count      // 消息数 (Value)
    ) {}
}
