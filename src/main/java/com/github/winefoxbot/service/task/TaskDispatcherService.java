package com.github.winefoxbot.service.task;

import com.github.winefoxbot.model.entity.ScheduleTask;

public interface TaskDispatcherService {

    void executeTask(ScheduleTask task) throws Exception;
}
