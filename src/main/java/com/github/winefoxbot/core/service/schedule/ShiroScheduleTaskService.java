package com.github.winefoxbot.core.service.schedule;

import com.baomidou.mybatisplus.extension.service.IService;
import com.github.winefoxbot.core.config.plugin.BasePluginConfig;
import com.github.winefoxbot.core.model.entity.ShiroScheduleTask;
import com.github.winefoxbot.core.model.enums.PushTargetType;
import com.github.winefoxbot.core.service.schedule.handler.BotJobHandler;
import org.jobrunr.jobs.lambdas.JobLambda;

import java.util.List;

/**
 * 定时任务服务接口
 * @author FlanChan
 */
public interface ShiroScheduleTaskService extends IService<ShiroScheduleTask> {

    /**
     * Handler 处理器方式调度 (支持传参)
     */
    void scheduleHandler(Long botId, PushTargetType targetType, Long targetId, String cron, String taskKey, String parameter);

    /**
     * Lambda 方式调度 (无参数)
     */
    void scheduleLambda(Long botId, PushTargetType targetType, Long targetId, String taskName, String cron, JobLambda jobLambda);

    /**
     * Class 处理器方式调度 (支持传参)
     */
    void scheduleHandler(Long botId, PushTargetType targetType, Long targetId, String cron, Class<? extends BotJobHandler<?,? extends BasePluginConfig>> handlerClass, String parameter);


    /**
     * Class 处理器方式调度 (无参)
     */
    void scheduleHandler(Long botId, PushTargetType targetType, Long targetId, String cron, Class<? extends BotJobHandler<?,? extends BasePluginConfig>> handlerClass);


    /**
     * 立即触发任务执行
     */
    void triggerTaskNow(Long botId, PushTargetType targetType, Long targetId, String taskKey);

    /**
     * 更新任务状态
     */
    void updateTaskStatus(Long botId, PushTargetType targetType, Long targetId, String taskKey, boolean enable);

    /**
     * 取消任务 (指定任务名)
     */
    void cancelTask(Long botId, PushTargetType targetType, Long targetId, String taskName);

    /**
     * 取消任务 (指定类)
     */
    void cancelTask(Long botId, PushTargetType targetType, Long targetId, Class<? extends BotJobHandler<?,? extends BasePluginConfig>> handlerClass);

    /**
     * 获取任务配置 
     */
    ShiroScheduleTask getTaskConfig(Long botId, PushTargetType targetType, Long targetId, String taskName);

    /**
     * 获取任务配置 （指定类）
     */
    ShiroScheduleTask getTaskConfig(Long botId, PushTargetType targetType, Long targetId, Class<? extends BotJobHandler<?,? extends BasePluginConfig>> handlerClass);

    /**
     * 获取列表 
     */
    List<ShiroScheduleTask> listTaskConfigs(Long botId, PushTargetType targetType);


    /**
     * 获取列表
     */
    List<ShiroScheduleTask> listTaskConfigs(Long botId, PushTargetType targetType ,Long targetId);

    /**
     * 生成任务 ID
     */
    String generateJobId(Long botId, PushTargetType targetType, Long targetId, String taskKey);

    /**
     * 解析任务键名
     */
    String resolveTaskKey(Class<? extends BotJobHandler<?,? extends BasePluginConfig>> handlerClass);
}
