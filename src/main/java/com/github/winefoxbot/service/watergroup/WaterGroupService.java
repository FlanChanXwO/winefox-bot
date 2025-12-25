package com.github.winefoxbot.service.watergroup;

import com.github.winefoxbot.model.entity.WaterGroupMessageStat;

import java.util.List;

/**
 * @author FlanChan (badapple495@outlook.com)
 * @since 2025-12-16-0:16
 */
public interface WaterGroupService {
    void incrementMessageCount(long groupId, long userId);

    List<WaterGroupMessageStat> getDailyRanking(long groupId);

}
