package com.github.winefoxbot.init;

import com.github.winefoxbot.schedule.DynamicTaskScheduler;
import com.github.winefoxbot.service.task.ScheduleTaskService; // 修复点 1: 依赖 Service 层
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class TaskLoaderRunner implements ApplicationRunner {

    // 依赖 Service 层，而不是直接依赖 Mapper
    private final ScheduleTaskService scheduleTaskService;
    private final DynamicTaskScheduler taskScheduler;

    @Override
    public void run(ApplicationArguments args) {
        log.info("应用启动，开始处理数据库中的调度任务...");
//
//        // 步骤 1: 清理已错过的单次任务
//        // 将所有状态为 PENDING、类型为 ONE_TIME 且执行时间已过的任务标记为 FAILED。
//        // 这样做可以防止这些“僵尸”任务被重复加载。
//        LocalDateTime now = LocalDateTime.now();
//        UpdateWrapper<ScheduleTask> cleanupWrapper = new UpdateWrapper<>();
//        cleanupWrapper
//                .eq("status", TaskStatus.PENDING)
//                .eq("schedule_type", ScheduleType.ONE_TIME)
//                .lt("next_execution_time", now);
//
//        cleanupWrapper.set("status", TaskStatus.FAILED); // 将状态设置为 FAILED
//
//        boolean updated = scheduleTaskService.update(cleanupWrapper);
//        if (updated) {
//            log.warn("已将部分错过的单次任务状态更新为 FAILED。");
//        }
//
//        // 步骤 2: 查询所有需要被调度的有效任务
//        // 修复点 2: 在数据库层面进行高效过滤
//        QueryWrapper<ScheduleTask> queryWrapper = new QueryWrapper<>();
//        queryWrapper.eq("status", TaskStatus.PENDING)
//                .and(qw -> qw
//                        // 条件A: 类型是周期性的 (所有 PENDING 的周期性任务都应该被加载)
//                        .in("schedule_type", ScheduleType.RECURRING_INDEFINITE, ScheduleType.RECURRING_FIXED_COUNT)
//                        .or()
//                        // 条件B: 类型是单次的，并且执行时间还未到
//                        .eq("schedule_type", ScheduleType.ONE_TIME).ge("next_execution_time", now)
//                );
//
//        List<ScheduleTask> tasksToSchedule = scheduleTaskService.list(queryWrapper);
//
//        if (tasksToSchedule.isEmpty()) {
//            log.info("没有找到需要加载的待处理任务。");
//            return;
//        }
//
//        log.info("发现 {} 个有效任务，准备将其加入调度器...", tasksToSchedule.size());
//
//        // 步骤 3: 调度所有有效任务
//        // 修复点 3: 循环体逻辑简化，因为无效任务已在查询时被过滤
//        tasksToSchedule.forEach(task -> {
//            try {
//                // 修复点 4: 使用正确的字段名 taskId
//                log.info("正在调度任务 [ID: {}], 类型: {}", task.getTaskId(), task.getScheduleType());
//                taskScheduler.scheduleTask(task);
//            } catch (Exception e) {
//                // 即使某个任务调度失败，也不应影响其他任务的加载
//                log.error("调度任务 [ID: {}] 时发生异常，将跳过此任务。", task.getTaskId(), e);
//            }
//        });

        log.info("所有有效任务已处理完毕。");
    }
}
