package com.github.winefoxbot.core.service.schedule;


import org.jobrunr.jobs.lambdas.JobLambda;

/**
* @author FlanChan
*/
public interface JobRunrScheduleTaskService {
    void scheduleOrUpdateRecurrentTask(String jobId, String cronExpression, JobLambda task);

    void deleteRecurrentTask(String jobId);
}
