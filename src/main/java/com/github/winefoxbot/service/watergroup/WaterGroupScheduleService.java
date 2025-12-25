package com.github.winefoxbot.service.watergroup;

import com.baomidou.mybatisplus.extension.service.IService;
import com.github.winefoxbot.model.entity.WaterGroupSchedule;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalTime;

/**
* @author FlanChan
* @description 针对表【water_group_schedule】的数据库操作Service
* @createDate 2025-12-24 11:53:16
*/
public interface WaterGroupScheduleService extends IService<WaterGroupSchedule> {

    void scheduleDailyPush(Long groupId, LocalTime time);

    @Transactional
    void editDailyPush(Long groupId, LocalTime time);

    WaterGroupSchedule getScheduleJob(Long groupId);

    boolean checkScheduled(Long groupId);

    void unscheduleDailyPush(Long groupId);
}
