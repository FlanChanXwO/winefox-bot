package com.github.winefoxbot.plugins.watergroup.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.github.winefoxbot.core.annotation.common.RedissonLock;
import com.github.winefoxbot.plugins.watergroup.mapper.WaterGroupMessageStatMapper;
import com.github.winefoxbot.plugins.watergroup.model.entity.WaterGroupMessageStat;
import com.github.winefoxbot.plugins.watergroup.service.WaterGroupService;
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
     * 增加用户发言次数
     */
    @Override
    @Transactional(rollbackFor = Exception.class) // 事务依然需要，保证数据库操作原子性
    @RedissonLock(prefix = "msg_stat:lock", key = "#groupId + ':' + #userId")
    public void incrementMessageCount(long groupId, long userId) {

        // 1. 尝试直接更新（这是最高频的操作）
        boolean dayUpdated = dayMapper.update(null, new LambdaUpdateWrapper<WaterGroupMessageStat>()
                .eq(WaterGroupMessageStat::getGroupId, groupId)
                .eq(WaterGroupMessageStat::getUserId, userId)
                .eq(WaterGroupMessageStat::getDate, LocalDate.now())
                .setSql("msg_count = msg_count + 1")) > 0;

        // 2. 如果更新失败，说明今天还没记录，执行插入
        if (!dayUpdated) {
            // 因为有 Redis 锁，这里不需要 try-catch 处理并发插入冲突了
            // 此时是线程安全的
            WaterGroupMessageStat newStat = new WaterGroupMessageStat();
            newStat.setGroupId(groupId);
            newStat.setUserId(userId);
            newStat.setMsgCount(1);
            newStat.setDate(LocalDate.now());

            // 仍然建议保留唯一的唯一索引(group_id, user_id, date)在数据库层面作为兜底
            dayMapper.insert(newStat);
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
