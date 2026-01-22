package com.github.winefoxbot.core.service.schedule.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.github.winefoxbot.core.annotation.schedule.BotTask;
import com.github.winefoxbot.core.config.plugin.BasePluginConfig;
import com.github.winefoxbot.core.init.BotTaskRegistry;
import com.github.winefoxbot.core.mapper.ShiroScheduleTaskMapper;
import com.github.winefoxbot.core.model.entity.ShiroScheduleTask;
import com.github.winefoxbot.core.model.enums.common.PushTargetType;
import com.github.winefoxbot.core.service.schedule.JobRunrScheduleTaskService;
import com.github.winefoxbot.core.service.schedule.ShiroJobRunner;
import com.github.winefoxbot.core.service.schedule.ShiroScheduleTaskService;
import com.github.winefoxbot.core.service.schedule.handler.BotJobHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jobrunr.jobs.lambdas.JobLambda;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 定时任务服务实现类
 * <p>
 * 核心职责：
 * 1. 管理数据库中的任务配置 (CRUD)
 * 2. 对接 JobRunr 进行实际的调度控制 (Schedule/Delete/Trigger)
 * 3. 负责任务执行时的运行环境准备 (Bot实例获取、参数转换、异常处理)
 *
 * @author FlanChan
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ShiroScheduleTaskServiceImpl extends ServiceImpl<ShiroScheduleTaskMapper, ShiroScheduleTask> implements ShiroScheduleTaskService {

    private final JobRunrScheduleTaskService jobRunrService;

    // 任务类型注册中心 (用于 Key <-> Class 转换)
    private final BotTaskRegistry taskRegistry;

    private final ShiroJobRunner shiroJobRunner;
    // ==================== 1. 核心调度入口 (WebUI 创建/更新) ====================

    /**
     * 根据 Handler 的 Key 创建或更新定时任务
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void scheduleHandler(Long botId, PushTargetType targetType, Long targetId, String cron, String taskKey, String parameter) {
        // 1. 校验 Key 是否合法
        Class<? extends BotJobHandler<?,? extends BasePluginConfig>> handlerClass = taskRegistry.getClassByKey(taskKey);
        if (handlerClass == null) {
            throw new IllegalArgumentException("无效的任务类型 Key: " + taskKey);
        }

        // 2. 生成 JobID 和 描述信息
        var jobId = generateJobId(botId, targetType, targetId, taskKey);
        var desc = formatDesc(targetType, targetId, handlerClass, taskKey);

        // 3. 提交给 JobRunr (周期任务)
        // 使用 Lambda 表达式封装执行逻辑，JobRunr 会序列化这个 Lambda
        jobRunrService.scheduleOrUpdateRecurrentTask(jobId, cron,
                () -> shiroJobRunner.runHandlerJob(botId, targetType, targetId, handlerClass, parameter)
        );

        // 4. 更新数据库记录
        upsertTaskRecord(botId, targetType, targetId, taskKey, parameter, cron, desc, true);
        log.info("Handler任务已调度: Bot=[{}], Key=[{}], JobId=[{}]", botId, taskKey, jobId);
    }

    /**
     * 兼容旧接口：直接通过 Class 进行调度 (最终也是转为 Key 处理)
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void scheduleHandler(Long botId, PushTargetType targetType, Long targetId, String cron, Class<? extends BotJobHandler<?,? extends BasePluginConfig>> handlerClass, String parameter) {
        this.scheduleHandler(botId, targetType, targetId, cron, resolveTaskKey(handlerClass), parameter);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void scheduleHandler(Long botId, PushTargetType targetType, Long targetId, String cron, Class<? extends BotJobHandler<?,? extends BasePluginConfig>> handlerClass) {
        this.scheduleHandler(botId, targetType, targetId, cron, resolveTaskKey(handlerClass), null);
    }

    // ==================== 2. Lambda 调度 (代码动态调用) ====================

    /**
     * 直接调度 Lambda 表达式 (通常用于开发调试或简单逻辑)
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void scheduleLambda(Long botId, PushTargetType targetType, Long targetId, String taskName, String cron, JobLambda jobLambda) {
        var jobId = generateJobId(botId, targetType, targetId, taskName);
        var desc = String.format("[%s] %s Lambda任务: %s", targetType.getDescription(), targetId, taskName);

        jobRunrService.scheduleOrUpdateRecurrentTask(jobId, cron, jobLambda);
        upsertTaskRecord(botId, targetType, targetId, taskName, null, cron, desc, true);
        log.info("Lambda任务已调度: Bot=[{}], Name=[{}]", botId, taskName);
    }

    // ==================== 3. 手动触发与启停控制 (新需求) ====================

    /**
     * 立即触发一次任务 (Fire-and-Forget)
     */
    @Override
    public void triggerTaskNow(Long botId, PushTargetType targetType, Long targetId, String taskKey) {
        // 1. 从数据库获取当前配置 (确保参数是最新的)
        ShiroScheduleTask task = this.getOne(buildWrapper(botId, targetType, targetId, taskKey));
        if (task == null) {
            throw new IllegalArgumentException("任务不存在，无法触发");
        }

        Class<? extends BotJobHandler<?,? extends BasePluginConfig>> handlerClass = taskRegistry.getClassByKey(taskKey);
        if (handlerClass == null) {
            throw new IllegalStateException("任务对应的代码处理器已失效: " + taskKey);
        }

        // 2. 准备参数
        Object param = task.getTaskParam(); // 这里的 param 可能是 Map 或 JSON String

        // 3. 调用 JobRunrService 的 triggerTask 接口
        // 这会将任务放入 JobRunr 的即时队列中执行
        jobRunrService.triggerTask(
                () -> shiroJobRunner.runHandlerJob(botId, targetType, targetId, handlerClass, param)
        );

        log.info("任务手动触发指令已发送: JobId=[{}]", generateJobId(botId, targetType, targetId, taskKey));
    }

    /**
     * 启用/禁用任务
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateTaskStatus(Long botId, PushTargetType targetType, Long targetId, String taskKey, boolean enable) {
        ShiroScheduleTask task = this.getOne(buildWrapper(botId, targetType, targetId, taskKey));
        if (task == null) {
            throw new IllegalArgumentException("任务不存在");
        }

        String jobId = generateJobId(botId, targetType, targetId, taskKey);

        if (enable) {
            // === 启用逻辑 ===
            if (task.getIsEnabled()) {
                return; // 幂等处理
            }

            Class<? extends BotJobHandler<?,? extends BasePluginConfig>> handlerClass = taskRegistry.getClassByKey(taskKey);

            // 恢复 JobRunr 调度 (重新提交 Recurrent Task)
            jobRunrService.scheduleOrUpdateRecurrentTask(jobId, task.getCronExpression(),
                    () -> shiroJobRunner.runHandlerJob(botId, targetType, targetId, handlerClass, task.getTaskParam())
            );

            task.setIsEnabled(true);
            log.info("任务已启用: {}", jobId);

        } else {
            // === 禁用逻辑 ===
            if (!task.getIsEnabled()) {
                return; // 幂等处理
            }

            // 从 JobRunr 移除 (物理删除调度器中的任务，但保留 DB 记录)
            jobRunrService.deleteRecurrentTask(jobId);

            task.setIsEnabled(false);
            log.info("任务已禁用 (DB保留): {}", jobId);
        }

        this.updateById(task);
    }

    // ==================== 4. 任务取消 (彻底删除) ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancelTask(Long botId, PushTargetType targetType, Long targetId, String taskName) {
        var jobId = generateJobId(botId, targetType, targetId, taskName);

        // 1. JobRunr 物理删除
        jobRunrService.deleteRecurrentTask(jobId);

        // 2. 数据库删除
        var removed = this.remove(buildWrapper(botId, targetType, targetId, taskName));

        if (removed) {
            log.info("任务已彻底删除: {}", jobId);
        }
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public void cancelTask(Long botId, PushTargetType targetType, Long targetId, Class<? extends BotJobHandler<?,? extends BasePluginConfig>> handlerClass) {
        cancelTask(botId, targetType, targetId, resolveTaskKey(handlerClass));
    }

    // ==================== 5. 查询接口 ====================

    @Override
    public ShiroScheduleTask getTaskConfig(Long botId, PushTargetType targetType, Long targetId, String taskName) {
        return this.getOne(buildWrapper(botId, targetType, targetId, taskName));
    }


    @Override
    public ShiroScheduleTask getTaskConfig(Long botId, PushTargetType targetType, Long targetId, Class<? extends BotJobHandler<?,? extends BasePluginConfig>> handlerClass) {
        return this.getOne(buildWrapper(botId, targetType, targetId, resolveTaskKey(handlerClass)));
    }


    @Override
    public List<ShiroScheduleTask> listTaskConfigs(Long botId, PushTargetType targetType) {
        return this.list(new LambdaQueryWrapper<ShiroScheduleTask>()
                .eq(ShiroScheduleTask::getBotId, botId)
                .eq(ShiroScheduleTask::getTargetType, targetType));
    }

    @Override
    public List<ShiroScheduleTask> listTaskConfigs(Long botId, PushTargetType targetType, Long targetId) {
        return this.list(new LambdaQueryWrapper<ShiroScheduleTask>()
                .eq(ShiroScheduleTask::getBotId, botId)
                .eq(ShiroScheduleTask::getTargetType, targetType)
                .eq(ShiroScheduleTask::getTargetId, targetId));
    }

    @Override
    public String generateJobId(Long botId, PushTargetType targetType, Long targetId, String taskKey) {
        return String.format("%s-%s-%s-%s", botId, targetType.getValue(), targetId, taskKey);
    }

    private LambdaQueryWrapper<ShiroScheduleTask> buildWrapper(Long botId, PushTargetType targetType, Long targetId, String taskType) {
        return new LambdaQueryWrapper<ShiroScheduleTask>()
                .eq(ShiroScheduleTask::getBotId, botId)
                .eq(ShiroScheduleTask::getTargetType, targetType)
                .eq(ShiroScheduleTask::getTargetId, targetId)
                .eq(ShiroScheduleTask::getTaskType, taskType);
    }

    private void upsertTaskRecord(Long botId, PushTargetType targetType, Long targetId, String taskKey,
                                  Object taskParam, String cron, String desc, boolean isEnabled) {
        ShiroScheduleTask task = this.getOne(buildWrapper(botId, targetType, targetId, taskKey));

        if (task == null) {
            task = ShiroScheduleTask.builder()
                    .botId(botId)
                    .targetType(targetType)
                    .targetId(targetId)
                    .taskType(taskKey)
                    .build();
        }

        task.setTaskParam(taskParam);
        task.setCronExpression(cron);
        task.setDescription(desc);
        task.setIsEnabled(isEnabled);

        this.saveOrUpdate(task);
    }

    private String formatDesc(PushTargetType targetType, Long targetId, Class<?> handlerClass, String taskKey) {
        BotTask meta = taskRegistry.getMetaByClass(handlerClass);
        String displayName = (meta != null) ? meta.name() : handlerClass.getSimpleName();
        return String.format("[%s] %s 执行 [%s]", targetType.getDescription(), targetId, displayName);
    }


    @Override
    public String resolveTaskKey(Class<? extends BotJobHandler<?,? extends BasePluginConfig>> handlerClass) {
        BotTask meta = taskRegistry.getMetaByClass(handlerClass);
        return (meta != null) ? meta.key() : handlerClass.getSimpleName();
    }
}