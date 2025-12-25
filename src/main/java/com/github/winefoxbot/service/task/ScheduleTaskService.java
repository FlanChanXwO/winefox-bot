package com.github.winefoxbot.service.task;


import com.baomidou.mybatisplus.extension.service.IService;
import com.github.winefoxbot.model.entity.ScheduleTask;

import java.util.Optional;

/**
* @author FlanChan
* @description 针对表【shiro_schedule_task】的数据库操作Service
* @createDate 2025-12-19 06:10:04
*/
public interface ScheduleTaskService extends IService<ScheduleTask> {

    ScheduleTask createScheduleTask(ScheduleTask taskToCreate);

    void handleTaskExecution(String taskId);

    boolean cancelScheduleTask(String taskId, String userId);

    void calculateAndSetNextExecutionTime(ScheduleTask task);
    /**
     * 根据任务的描述信息查找一个有效的（非FAILED）任务。
     *
     * @param taskId 任务的唯一ID
     * @return 如果找到有效的任务，则返回 Optional<ScheduleTask>，否则返回 Optional.empty()
     */
    Optional<ScheduleTask> findTaskByTaskId(String taskId);
}
