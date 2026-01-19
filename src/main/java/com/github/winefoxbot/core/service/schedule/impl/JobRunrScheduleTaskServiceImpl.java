package com.github.winefoxbot.core.service.schedule.impl;

import com.github.winefoxbot.core.mapper.JobRunrJobMapper;
import com.github.winefoxbot.core.service.schedule.JobRunrScheduleTaskService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jobrunr.jobs.lambdas.JobLambda;
import org.jobrunr.scheduling.JobScheduler;
import org.springframework.stereotype.Service;

import java.time.ZoneId;

/**
 * @author FlanChan
 * @description 针对表【shiro_schedule_task】的数据库操作Service实现
 * @createDate 2025-12-19 06:10:04
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class JobRunrScheduleTaskServiceImpl implements JobRunrScheduleTaskService {
    private final JobRunrJobMapper jobRunrJobMapper;
    private final ZoneId targetZoneId = ZoneId.of("Asia/Shanghai");
    private final JobScheduler jobScheduler;

    @Override
    public void scheduleOrUpdateRecurrentTask(String jobId, String cronExpression, JobLambda task) {
        jobRunrJobMapper.deleteScheduledJobsByRecurringId(jobId);
        jobScheduler.scheduleRecurrently(
                jobId,
                cronExpression,
                targetZoneId,
                task
        );
    }

    @Override
    public void deleteRecurrentTask(String jobId) {
        jobRunrJobMapper.deleteScheduledJobsByRecurringId(jobId);
        jobScheduler.deleteRecurringJob(jobId);
    }
}