package com.github.winefoxbot.service.task.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.cronutils.model.Cron;
import com.cronutils.model.CronType;
import com.cronutils.model.definition.CronDefinitionBuilder;
import com.cronutils.parser.CronParser;
import com.github.winefoxbot.mapper.ScheduleTaskMapper;
import com.github.winefoxbot.model.entity.ScheduleTask;
import com.github.winefoxbot.model.enums.ScheduleType;
import com.github.winefoxbot.model.enums.TaskStatus;
import com.github.winefoxbot.schedule.DynamicTaskScheduler;
import com.github.winefoxbot.service.task.ScheduleTaskService;
import com.github.winefoxbot.service.task.TaskDispatcherService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;
import java.util.Optional;
import java.util.UUID;

/**
 * @author FlanChan
 * @description 针对表【shiro_schedule_task】的数据库操作Service实现
 * @createDate 2025-12-19 06:10:04
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ScheduleTaskServiceImpl extends ServiceImpl<ScheduleTaskMapper, ScheduleTask>
        implements ScheduleTaskService {
    private final DynamicTaskScheduler taskScheduler;
    private final TaskDispatcherService taskDispatcherService;

    private static final CronParser cronParser = new CronParser(CronDefinitionBuilder.instanceDefinitionFor(CronType.SPRING));

    /**
     * 创建调度任务，直接接收一个部分填充的 ScheduleTask 对象。
     * 这个方法会负责补全系统管理的字段并将其持久化。
     *
     * @param taskToCreate 一个包含了创建任务所需信息（如 beanName, methodName, scheduleType 等）的实体对象。
     * @return 持久化后完整的任务实体。
     */
    @Override
    @Transactional
    public ScheduleTask createScheduleTask(ScheduleTask taskToCreate) {
        // --- 系统强制设定的字段，确保数据完整性和安全性 ---
        // 1. 生成全局唯一的主键 ID
        taskToCreate.setId(UUID.randomUUID().toString());
        // 2. 生成唯一的任务ID，覆盖任何传入的值
        taskToCreate.setTaskId(taskToCreate.getTaskId());
        // 3. 初始化状态为 PENDING
        taskToCreate.setStatus(TaskStatus.PENDING);
        // 4. 初始化执行次数为 0
        taskToCreate.setExecutedCount(0);
        // createTime 和 updateTime 将由 MybatisPlusMetaObjectHandler 自动填充

        // --- 根据业务逻辑计算并设置 nextExecutionTime ---
        if (taskToCreate.getScheduleType() == ScheduleType.ONE_TIME) {
            // 对于单次任务，nextExecutionTime 应该由调用者在 taskToCreate 对象中提供
            if (taskToCreate.getNextExecutionTime() == null) {
                throw new IllegalArgumentException("单次任务必须提供 executionTime。");
            }
        } else { // 对于周期性任务
            if (taskToCreate.getCronExpression() == null || taskToCreate.getCronExpression().isBlank()) {
                throw new IllegalArgumentException("周期性任务必须提供 cronExpression。");
            }
            // 如果是固定次数的周期任务，totalExecutions 应该已设置
            if (taskToCreate.getScheduleType() == ScheduleType.RECURRING_FIXED_COUNT && taskToCreate.getTotalExecutions() == null) {
                throw new IllegalArgumentException("固定次数的周期任务必须提供 totalExecutions。");
            }
            // 计算第一次执行时间
            calculateAndSetNextExecutionTime(taskToCreate);
        }

        // 持久化任务
        this.save(taskToCreate);

        // 将任务添加到调度器
        taskScheduler.scheduleTask(taskToCreate);

        log.info("新任务 [ID: {}] 已创建并调度。", taskToCreate.getTaskId());
        return taskToCreate;
    }


    /**
     * 周期性任务的核心处理逻辑
     *
     * @param taskId 任务ID
     */
    @Override
    @Transactional
    public void handleTaskExecution(String taskId) {
        Optional<ScheduleTask> taskOptional = this.findTaskByTaskId(taskId);

        // 防御性检查
        if (taskOptional.isEmpty() || taskOptional.get().getStatus() != TaskStatus.PENDING) {
            log.warn("任务 [ID: {}] 不存在或状态不是PENDING，跳过本次执行。", taskId);
            // 如果任务不是PENDING，则从调度器中移除，防止无效触发
            if (taskOptional != null) {
                taskScheduler.cancelTask(taskOptional.get().getTaskId());
            }
            return;
        }
        // --- 执行核心业务逻辑 ---
        ScheduleTask task = taskOptional.get();
        try {
            log.info("开始执行任务 [ID: {}], 类型: {}", task.getTaskId(), task.getScheduleType());
            taskDispatcherService.executeTask(task); // 委托给分发器执行
            log.info("任务 [ID: {}] 本次执行成功。", task.getTaskId());
        } catch (Exception e) {
            log.error("任务 [ID: {}] 本次执行失败。", task.getTaskId(), e);
        } finally {
            // --- 更新任务状态和下一次执行时间 ---
            task.setExecutedCount(task.getExecutedCount() + 1);

            boolean shouldContinue = true;
            if (task.getScheduleType() == ScheduleType.ONE_TIME) {
                task.setStatus(TaskStatus.COMPLETED);
                shouldContinue = false;
            } else if (task.getScheduleType() == ScheduleType.RECURRING_FIXED_COUNT) {
                if (task.getExecutedCount() >= task.getTotalExecutions()) {
                    task.setStatus(TaskStatus.COMPLETED);
                    log.info("任务 [ID: {}] 已达到执行次数上限 ({})，标记为COMPLETED。", taskId, task.getTotalExecutions());
                    shouldContinue = false;
                }
            }

            if (shouldContinue) {
                // 如果是周期性任务，计算下一次执行时间
                calculateAndSetNextExecutionTime(task);
            } else {
                // 任务完成或终止，从调度器中移除
                taskScheduler.cancelTask(task.getTaskId());
            }

            this.updateById(task);
        }
    }

    @Override
    @Transactional
    public boolean cancelScheduleTask(String taskId, String userId) {
        Optional<ScheduleTask> taskOptional = this.findTaskByTaskId(taskId);
        // 允许取消 PENDING 状态的任务，并验证用户所有权
        if (taskOptional.isEmpty() || taskOptional.get().getStatus() != TaskStatus.PENDING) {
            log.warn("取消任务 [ID: {}] 失败：任务不存在、用户不匹配或任务状态不为PENDING。", taskId);
            return false;
        }
        ScheduleTask task = taskOptional.get();
        // 从调度器中取消
        taskScheduler.cancelTask(task.getTaskId());
        // 更新数据库状态
        task.setStatus(TaskStatus.CANCELED);
        this.updateById(task);
        log.info("用户 [{}] 成功取消任务 [ID: {}]", userId, taskId);
        return true;
    }

    /**
     * 根据 Cron 表达式计算并设置任务的下一次执行时间
     *
     * @param task 任务对象
     */
    @Override
    public void calculateAndSetNextExecutionTime(ScheduleTask task) {
        if (task.getCronExpression() != null) {
            try {
                Cron cron = cronParser.parse(task.getCronExpression());
                ZonedDateTime now = ZonedDateTime.now();
                Optional<ZonedDateTime> nextExecution = com.cronutils.model.time.ExecutionTime.forCron(cron).nextExecution(now);

                nextExecution.ifPresent(zdt -> task.setNextExecutionTime(zdt.toLocalDateTime()));
            } catch (IllegalArgumentException e) {
                log.error("任务 [ID: {}] 的 Cron 表达式 '{}' 无效。", task.getTaskId(), task.getCronExpression(), e);
                // Cron 表达式无效，将任务标记为失败并取消
                task.setStatus(TaskStatus.FAILED);
                taskScheduler.cancelTask(task.getTaskId());
            }
        }
    }

    @Override
    public Optional<ScheduleTask> findTaskByTaskId(String taskId) {
        return Optional.ofNullable(
                this.lambdaQuery()
                        .eq(ScheduleTask::getTaskId, taskId)
                        .one()
        );
    }
}




