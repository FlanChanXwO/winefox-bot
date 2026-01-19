package com.github.winefoxbot.plugins.pixiv.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.github.winefoxbot.plugins.pixiv.model.entity.PixivRankPushSchedule;
import com.github.winefoxbot.plugins.pixiv.model.enums.PixivRankPushMode;

import java.util.List;

/**
* @author FlanChan
* @description 针对表【pixiv_rank_push_schedule】的数据库操作Service
* @createDate 2025-12-28 19:46:20
*/
public interface PixivRankPushScheduleService {

    void schedulePush(Long groupId, PixivRankPushMode mode, String cronExpression, String description);

    boolean unschedulePush(Long groupId, PixivRankPushMode rankType);

    List<PixivRankPushSchedule> getSchedulesByGroupId(Long groupId);

}
