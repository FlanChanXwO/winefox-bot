package com.github.winefoxbot.core.service.schedule.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.github.winefoxbot.core.mapper.GroupPushScheduleMapper;
import com.github.winefoxbot.core.model.entity.GroupPushSchedule;
import com.github.winefoxbot.core.service.schedule.GroupPushScheduleService;
import com.github.winefoxbot.core.service.schedule.ScheduleTaskService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jobrunr.jobs.lambdas.JobLambda;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class GroupPushScheduleServiceImpl extends ServiceImpl<GroupPushScheduleMapper, GroupPushSchedule>
    implements GroupPushScheduleService {

    private final ScheduleTaskService scheduleTaskService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void scheduleTask(Long groupId, String taskType, String taskParam, String cronExpression, String description, JobLambda task) {
        String jobId = generateJobId(groupId, taskType, taskParam);

        // 1. 提交到 JobRunr
        scheduleTaskService.scheduleOrUpdateRecurrentTask(jobId, cronExpression, task);

        // 2. 更新或保存数据库配置
        GroupPushSchedule schedule = getTaskConfig(groupId, taskType, taskParam);
        if (schedule == null) {
            schedule = new GroupPushSchedule();
            schedule.setGroupId(groupId);
            schedule.setTaskType(taskType);
            schedule.setTaskParam(taskParam);
        }
        schedule.setCronExpression(cronExpression);
        schedule.setDescription(description);
        schedule.setIsEnabled(true);

        this.saveOrUpdate(schedule);
        log.info("已调度群组推送任务: JobId=[{}], GroupId=[{}], Type=[{}]", jobId, groupId, taskType);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void unscheduleTask(Long groupId, String taskType, String taskParam) {
        String jobId = generateJobId(groupId, taskType, taskParam);

        // 1. 从 JobRunr 删除
        scheduleTaskService.deleteRecurrentTask(jobId);

        // 2. 从数据库删除
        LambdaQueryWrapper<GroupPushSchedule> wrapper = new LambdaQueryWrapper<GroupPushSchedule>()
                .eq(GroupPushSchedule::getGroupId, groupId)
                .eq(GroupPushSchedule::getTaskType, taskType);

        if (taskParam != null) {
            wrapper.eq(GroupPushSchedule::getTaskParam, taskParam);
        } else {
            wrapper.isNull(GroupPushSchedule::getTaskParam);
        }

        boolean removed = this.remove(wrapper);
        if (removed) {
            log.info("已取消群组推送任务: JobId=[{}]", jobId);
        } else {
            log.warn("尝试取消不存在的群组推送任务: JobId=[{}]", jobId);
        }
    }

    @Override
    public GroupPushSchedule getTaskConfig(Long groupId, String taskType, String taskParam) {
        LambdaQueryWrapper<GroupPushSchedule> wrapper = new LambdaQueryWrapper<GroupPushSchedule>()
                .eq(GroupPushSchedule::getGroupId, groupId)
                .eq(GroupPushSchedule::getTaskType, taskType);

        if (taskParam != null) {
            wrapper.eq(GroupPushSchedule::getTaskParam, taskParam);
        } else {
            wrapper.isNull(GroupPushSchedule::getTaskParam);
        }

        return this.getOne(wrapper);
    }

    @Override
    public List<GroupPushSchedule> listTaskConfigs(Long groupId, String taskType) {
        return this.list(new LambdaQueryWrapper<GroupPushSchedule>()
                .eq(GroupPushSchedule::getGroupId, groupId)
                .eq(GroupPushSchedule::getTaskType, taskType));
    }

    private String generateJobId(Long groupId, String taskType, String taskParam) {
        if (taskParam == null || taskParam.isBlank()) {
            return String.format("%s-%s", taskType, groupId);
        }
        return String.format("%s-%s-%s", taskType, taskParam, groupId);
    }
}
