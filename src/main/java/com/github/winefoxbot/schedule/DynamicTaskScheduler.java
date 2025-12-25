package com.github.winefoxbot.schedule;

import com.github.winefoxbot.model.entity.ScheduleTask;
import com.github.winefoxbot.model.enums.ScheduleType;
import com.github.winefoxbot.service.task.ScheduleTaskService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.stereotype.Component;

import java.time.ZoneId;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;

@Slf4j
@Component
public class DynamicTaskScheduler {
    private final TaskScheduler taskScheduler;
    private final ScheduleTaskService scheduleTaskService;

    // 手动创建构造函数，并在 scheduleTaskService 参数上添加 @Lazy
    public DynamicTaskScheduler(TaskScheduler taskScheduler, @Lazy ScheduleTaskService scheduleTaskService) {
        this.taskScheduler = taskScheduler;
        this.scheduleTaskService = scheduleTaskService;
    }


    private final Map<String, ScheduledFuture<?>> scheduledTasks = new ConcurrentHashMap<>();

    public void scheduleTask(ScheduleTask task) {
        if (task == null || task.getTaskId() == null) {
            log.error("无法调度一个空任务或没有ID的任务。");
            return;
        }

        // 如果任务已在调度中，先取消旧的，这对于更新任务逻辑很重要
        if (scheduledTasks.containsKey(task.getTaskId())) {
            cancelTask(task.getTaskId());
        }

        // 确保任务有下一次执行时间
        if (task.getNextExecutionTime() == null && task.getCronExpression() == null) {
            log.error("任务 [ID: {}] 既没有 nextExecutionTime 也没有 cronExpression，无法调度。", task.getTaskId());
            return;
        }

        ScheduledFuture<?> future;
        Runnable taskRunner = () -> {
            try {
                scheduleTaskService.handleTaskExecution(task.getTaskId());
            } catch (Exception e) {
                log.error("任务 [ID: {}] 的调度触发执行过程中发生未捕获的顶层异常。", task.getTaskId(), e);
            }
        };

        if (task.getScheduleType() == ScheduleType.ONE_TIME) {
            // 单次任务，在指定时间点执行
            future = taskScheduler.schedule(
                    taskRunner,
                    task.getNextExecutionTime().atZone(ZoneId.systemDefault()).toInstant()
            );
        } else {
            // 周期性任务，使用 Cron 表达式
            if (task.getCronExpression() == null || task.getCronExpression().isBlank()) {
                log.error("周期性任务 [ID: {}] 缺少 Cron 表达式，无法调度。", task.getTaskId());
                return;
            }
            try {
                CronTrigger cronTrigger = new CronTrigger(task.getCronExpression());
                future = taskScheduler.schedule(taskRunner, cronTrigger);
            } catch (IllegalArgumentException e) {
                log.error("任务 [ID: {}] 的 Cron 表达式 '{}' 无效，无法调度。", task.getTaskId(), task.getCronExpression(), e);
                return;
            }
        }

        if (future != null) {
            scheduledTasks.put(task.getTaskId(), future);
            log.info("任务 [ID: {}] 已成功添加至内存调度器。", task.getTaskId());
        }
    }

    public void cancelTask(String taskId) {
        // 修复点 3: 确保所有地方都使用 String 类型的 taskId
        ScheduledFuture<?> future = scheduledTasks.remove(taskId); // remove 会返回被移除的值
        if (future != null) {
            future.cancel(false); // false 表示不中断正在执行的任务
            log.info("内存中的任务 [ID: {}] 已成功取消。", taskId);
        } else {
            log.warn("尝试取消一个不在内存调度器中的任务 [ID: {}]，可能已被执行、取消或从未调度。", taskId);
        }
    }
}
