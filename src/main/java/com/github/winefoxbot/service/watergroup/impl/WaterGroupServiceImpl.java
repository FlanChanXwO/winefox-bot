package com.github.winefoxbot.service.watergroup.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.github.winefoxbot.mapper.WaterGroupMessageStatMapper;
import com.github.winefoxbot.model.entity.WaterGroupMessageStat;
import com.github.winefoxbot.service.watergroup.WaterGroupService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class WaterGroupServiceImpl implements WaterGroupService {

    private final WaterGroupMessageStatMapper dayMapper;

    /**
     * 增加用户发言次数，处理日和月统计
     * 采用 "先更新，若失败则插入" 的方式，兼容所有数据库
     */
    @Transactional
    @Override
    public void incrementMessageCount(long groupId, long userId) {

        boolean dayUpdated = dayMapper.update(null, new LambdaUpdateWrapper<WaterGroupMessageStat>()
                .eq(WaterGroupMessageStat::getGroupId, groupId)
                .eq(WaterGroupMessageStat::getUserId, userId)
                .eq(WaterGroupMessageStat::getDate, LocalDate.now())
                .setSql("msg_count = msg_count + 1")) > 0;

        if (!dayUpdated) {
            try {
                WaterGroupMessageStat newStat = new WaterGroupMessageStat();
                newStat.setGroupId(groupId);
                newStat.setUserId(userId);
                newStat.setMsgCount(1);
                newStat.setDate(LocalDate.now());
                dayMapper.insert(newStat);
            } catch (Exception e) {
                // 处理并发冲突：如果另一个线程刚刚插入，这里会失败，再尝试更新一次
                log.warn("Concurrent insert conflict for day stat, retrying update for user {} in group {}", userId, groupId);
                dayMapper.update(null, new LambdaUpdateWrapper<WaterGroupMessageStat>()
                        .eq(WaterGroupMessageStat::getGroupId, groupId)
                        .eq(WaterGroupMessageStat::getUserId, userId)
                        .setSql("msg_count = msg_count + 1"));
            }
        }
    }

    /**
     * 获取指定群组的日发言排行数据
     *
     * @param groupId 群号
     * @return 包含用户信息的排行列表
     */
    @Override
    public List<WaterGroupMessageStat> getDailyRanking(long groupId) {
        return dayMapper.selectList(new LambdaQueryWrapper<WaterGroupMessageStat>()
                .eq(WaterGroupMessageStat::getGroupId, groupId)
                .eq(WaterGroupMessageStat::getDate, LocalDate.now()) // 只查询今天的数据
                .gt(WaterGroupMessageStat::getMsgCount, 0) // 只看发言过的
                .orderByDesc(WaterGroupMessageStat::getMsgCount)
                .last("LIMIT 20"));
    }


}
