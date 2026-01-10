package com.github.winefoxbot.core.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.github.winefoxbot.core.model.entity.GroupPushSchedule;
import org.apache.ibatis.annotations.Mapper;

/**
 * @author FlanChan
 * @description 针对表【group_push_schedule(统一的群组推送日程表)】的数据库操作Mapper
 * @createDate 2025-12-28 20:00:00
 * @Entity com.github.winefoxbot.core.model.entity.GroupPushSchedule
 */
@Mapper
public interface GroupPushScheduleMapper extends BaseMapper<GroupPushSchedule> {

}

