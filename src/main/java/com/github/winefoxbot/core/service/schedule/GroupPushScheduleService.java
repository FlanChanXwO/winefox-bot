package com.github.winefoxbot.core.service.schedule;

import com.baomidou.mybatisplus.extension.service.IService;
import com.github.winefoxbot.core.model.entity.GroupPushSchedule;
import org.jobrunr.jobs.lambdas.JobLambda;

import java.util.List;

/**
 * 统一的群组推送日程配置服务
 * 负责管理群组的推送任务配置以及对接JobRunr调度
 */
public interface GroupPushScheduleService extends IService<GroupPushSchedule> {

    /**
     * 调度或更新一个推送任务
     * @param groupId 群组ID
     * @param taskType 任务类型 (e.g. "WATER_GROUP_STAT")
     * @param taskParam 任务参数 (e.g. "daily", 可为null)
     * @param cronExpression Cron表达式
     * @param description 描述
     * @param task 实际执行的任务Lambda
     */
    void scheduleTask(Long groupId, String taskType, String taskParam, String cronExpression, String description, JobLambda task);

    /**
     * 取消一个推送任务
     * @param groupId 群组ID
     * @param taskType 任务类型
     * @param taskParam 任务参数
     */
    void unscheduleTask(Long groupId, String taskType, String taskParam);

    GroupPushSchedule getTaskConfig(Long groupId, String taskType, String taskParam);

    List<GroupPushSchedule> listTaskConfigs(Long groupId, String taskType);
}
