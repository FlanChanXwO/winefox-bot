package com.github.winefoxbot.plugins.watergroup.service;

import com.github.winefoxbot.plugins.watergroup.model.entity.WaterGroupMessageStat;

import java.util.List;

/**
 * @author FlanChan (badapple495@outlook.com)
 * @since 2025-12-16-0:16
 */
public interface WaterGroupService {
    void incrementMessageCount(long groupId, long userId);

    List<WaterGroupMessageStat> getDailyRanking(long groupId);

}
