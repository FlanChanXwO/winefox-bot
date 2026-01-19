package com.github.winefoxbot.core.service.schedule.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.winefoxbot.core.annotation.plugin.Plugin;
import com.github.winefoxbot.core.mapper.ShiroScheduleTaskMapper;
import com.github.winefoxbot.core.model.entity.ShiroScheduleTask;
import com.github.winefoxbot.core.model.enums.PushTargetType;
import com.github.winefoxbot.core.service.schedule.JobRunrScheduleTaskService;
import com.github.winefoxbot.core.service.schedule.ShiroScheduleTaskService;
import com.github.winefoxbot.core.service.schedule.handler.BotJobHandler;
import com.mikuac.shiro.core.Bot;
import com.mikuac.shiro.core.BotContainer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jobrunr.jobs.annotations.Job;
import org.jobrunr.jobs.lambdas.JobLambda;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.function.Consumer;

/**
 * @author FlanChan
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ShiroScheduleTaskServiceImpl extends ServiceImpl<ShiroScheduleTaskMapper, ShiroScheduleTask> implements ShiroScheduleTaskService {

    private final JobRunrScheduleTaskService jobRunrService;
    private final BotContainer botContainer;
    private final ApplicationContext applicationContext;
    private final ObjectMapper objectMapper;

    // ==================== 1. Lambda 调度实现 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void scheduleLambda(Long botId, PushTargetType targetType, Long targetId, String taskName, String cron, JobLambda jobLambda) {
        // 1. 生成隔离的 JobID
        var jobId = generateJobId(botId, targetType, targetId, taskName);
        var desc = formatDesc(targetType, targetId, taskName);

        // 2. JobRunr 调度
        jobRunrService.scheduleOrUpdateRecurrentTask(jobId, cron, jobLambda);

        // 3. 数据库 UPSERT
        upsertTaskRecord(botId, targetType, targetId, taskName, null, cron, desc);
        log.info("Lambda任务已调度: Bot=[{}], JobId=[{}], Cron=[{}]", botId, jobId, cron);
    }

    // ==================== 2. Handler Class 调度实现 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void scheduleHandler(Long botId, PushTargetType targetType, Long targetId, String cron, Class<? extends BotJobHandler<?>> handlerClass, String parameter) {
        var taskName = resolveTaskName(handlerClass);
        var jobId = generateJobId(botId, targetType, targetId, taskName);
        var desc = formatDesc(targetType, targetId, taskName);

        // JobRunr 调度
        // 注意：JobRunr 会自动序列化这个 parameter 对象
        jobRunrService.scheduleOrUpdateRecurrentTask(jobId, cron,
                () -> this.runHandlerJob(botId, targetType, targetId, handlerClass, parameter)
        );

        // 数据库记录
        upsertTaskRecord(botId, targetType, targetId, taskName, parameter, cron, desc);
        log.info("Class任务调度: Bot=[{}], Class=[{}]", botId, handlerClass.getSimpleName());
    }

    /**
     * JobRunr 回调入口 (必须 Public)
     */
    @Job(name = "HandlerJob: %0", retries = 5)
    public void runHandlerJob(Long botId, PushTargetType targetType, Long targetId,
                              // 修改点1：加上 <?>，表示通配符，消除了 Raw use 警告
                              Class<? extends BotJobHandler<?>> handlerClass,
                              Object parameter) {

        try {
            executeInternal(botId, targetType, targetId, handlerClass.getSimpleName(), bot -> {

                // 1. 获取实例 (这里的 getHandlerInstance 签名也要对应改一下，见下文)
                BotJobHandler<?> handler = getHandlerInstance(handlerClass);

                // 2. 参数转换 (利用反射获取泛型 T 的真实类型)
                // 这里需要一点反射技巧来拿到 T 的 Class，或者简单地在 convertParameter 里处理
                Object typedParam = convertParameter(handler, parameter);

                // 3. 执行
                // 修改点2：因为 handler 是 <?>，编译器不允许直接传 typedParam。
                // 我们必须告诉编译器：“放心，我确定这个参数是匹配的”，所以强转为 <Object>
                @SuppressWarnings("unchecked")
                BotJobHandler<Object> unsafeHandler = (BotJobHandler<Object>) handler;

                unsafeHandler.run(bot, targetId, typedParam);
            });
        } catch (Exception e) {
            log.error("任务执行失败，等待 JobRunr 重试... Task=[{}]", handlerClass.getSimpleName(), e);
            // 遇到 NullPointer 等代码错误，不要重试
            if (e instanceof NullPointerException || e instanceof IllegalArgumentException) {
                return;
            }
            throw e;
        }
    }

    /**
     * 辅助方法：将 Map/JSONObject 转为 Handler 需要的 T 类型
     */
    private Object convertParameter(BotJobHandler<?> handler, Object rawParam) {
        if (rawParam == null) {
            return null;
        }

        // 获取 Handler 实现类中 run 方法的第三个参数类型 (即 T 的实际类型)
        // 这里简化处理：假设 run 方法没有被重载，找到第一个 run 方法即可
        // 更严谨的做法是查找名为 run 且参数数量为 3 的方法
        var methods = handler.getClass().getMethods();
        Class<?> targetType = Object.class;

        for (var method : methods) {
            if ("run".equals(method.getName()) && method.getParameterCount() == 3) {
                // 第3个参数就是 T
                targetType = method.getParameterTypes()[2];
                break;
            }
        }

        // 如果已经是目标类型，直接返回
        if (targetType.isInstance(rawParam)) {
            return rawParam;
        }

        // 否则使用 Jackson 强转 (例如 LinkedHashMap -> MyConfigDto)
        return objectMapper.convertValue(rawParam, targetType);
    }

    /**
     * 获取 Handler 实例
     * 策略：优先从 Spring 容器获取（支持依赖注入），如果获取失败则通过反射创建
     */
    private BotJobHandler<?> getHandlerInstance(Class<? extends BotJobHandler<?>> handlerClass) {
        try {
            return applicationContext.getBean(handlerClass);
        } catch (Exception e) {
            try {
                return handlerClass.getDeclaredConstructor().newInstance();
            } catch (Exception ex) {
                throw new RuntimeException("无法实例化 Handler", ex);
            }
        }
    }


    // ==================== 3. 取消与查询 (Bot 隔离核心) ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancelTask(Long botId, PushTargetType targetType, Long targetId, String taskName) {
        var jobId = generateJobId(botId, targetType, targetId, taskName);

        // 1. JobRunr 物理删除 (ID 包含 BotId，安全)
        jobRunrService.deleteRecurrentTask(jobId);

        // 2. 数据库删除 (Wrapper 包含 BotId，安全)
        var removed = this.remove(buildWrapper(botId, targetType, targetId, taskName));

        if (removed) {
            log.info("任务已取消: Bot=[{}], JobId=[{}]", botId, jobId);
        } else {
            // 即便数据库没有，JobRunr 也已清理，确保不泄露
            log.warn("任务取消(DB未找到): Bot=[{}], JobId=[{}]", botId, jobId);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancelTask(Long botId, PushTargetType targetType, Long targetId, Class<?> handlerClass) {
        cancelTask(botId, targetType, targetId, resolveTaskName(handlerClass));
    }

    @Override
    public ShiroScheduleTask getTaskConfig(Long botId, PushTargetType targetType, Long targetId, String taskName) {
        return this.getOne(buildWrapper(botId, targetType, targetId, taskName));
    }

    @Override
    public List<ShiroScheduleTask> listTaskConfigs(Long botId, PushTargetType targetType, Long targetId) {
        // 列表查询也必须带 BotId
        return this.list(new LambdaQueryWrapper<ShiroScheduleTask>()
                .eq(ShiroScheduleTask::getBotId, botId)
                .eq(ShiroScheduleTask::getTargetType, targetType)
                .eq(ShiroScheduleTask::getTargetId, targetId));
    }

    // ==================== 4. 辅助方法 ====================

    /**
     * 生成全隔离 JobID: botId:type:targetId:taskName
     */
    private String generateJobId(Long botId, PushTargetType targetType, Long targetId, String taskName) {
        return String.format("%s:%s:%s:%s", botId, targetType.getValue(), targetId, taskName);
    }

    /**
     * 构建全隔离查询条件
     */
    private LambdaQueryWrapper<ShiroScheduleTask> buildWrapper(Long botId, PushTargetType targetType, Long targetId, String taskType) {
        return new LambdaQueryWrapper<ShiroScheduleTask>()
                .eq(ShiroScheduleTask::getBotId, botId)
                .eq(ShiroScheduleTask::getTargetType, targetType)
                .eq(ShiroScheduleTask::getTargetId, targetId)
                .eq(ShiroScheduleTask::getTaskType, taskType);
    }

    /**
     * 数据库记录更新 (适配实体类)
     */
    private void upsertTaskRecord(Long botId, PushTargetType targetType, Long targetId, String taskType, Object taskParam, String cron, String desc) {
        // 查找现有记录
        ShiroScheduleTask task = this.getOne(buildWrapper(botId, targetType, targetId, taskType));

        if (task == null) {
            // 新建
            task = ShiroScheduleTask.builder()
                    .botId(botId)
                    .targetType(targetType)
                    .targetId(targetId)
                    .taskType(taskType)
                    .build();
        }

        // 更新字段
        task.setTaskParam(taskParam);
        task.setCronExpression(cron);
        task.setDescription(desc);
        task.setIsEnabled(true);

        this.saveOrUpdate(task);
    }

    private String formatDesc(PushTargetType targetType, Long targetId, String taskName) {
        return String.format("[%s] %s 执行 [%s]", targetType.getDescription(), targetId, taskName);
    }

    private String resolveTaskName(Class<?> clazz) {
        if (clazz.isAnnotationPresent(Plugin.class)) {
            var name = clazz.getAnnotation(Plugin.class).name();
            if (name != null && !name.isEmpty()) {
                return name;
            }
        }
        return clazz.getSimpleName();
    }

    /**
     * 内部执行器
     * 注意：不再捕获异常，而是让异常抛出给 JobRunr 接管，从而触发重试机制
     */
    private void executeInternal(Long botId, PushTargetType targetType, Long targetId,
                                 String taskName, Consumer<Bot> action) {
        Bot bot = botContainer.robots.get(botId);

        // 1. 如果 Bot 不在线，抛出异常以触发重试
        // 场景：Bot 临时断线重连中，任务应该等待而不是直接丢弃
        if (bot == null) {
            String msg = String.format("Bot [%s] 离线，任务 [%s] 稍后重试. 目标: [%s-%s]",
                    botId, taskName, targetType, targetId);
            log.warn(msg);
            throw new IllegalStateException(msg);
        }

        // 2. 直接执行逻辑
        // 如果 action 内部报错（如网络超时、参数错误），异常会向上冒泡给 JobRunr
        action.accept(bot);
    }


}
