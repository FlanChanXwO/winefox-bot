package com.github.winefoxbot.core.service.schedule;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.winefoxbot.core.config.plugin.BasePluginConfig;
import com.github.winefoxbot.core.context.BotContext;
import com.github.winefoxbot.core.model.entity.ShiroScheduleTask;
import com.github.winefoxbot.core.model.enums.common.PushTargetType;
import com.github.winefoxbot.core.service.schedule.handler.BotJobHandler;
import com.mikuac.shiro.core.Bot;
import com.mikuac.shiro.core.BotContainer;
import com.mikuac.shiro.dto.event.message.GroupMessageEvent;
import com.mikuac.shiro.dto.event.message.PrivateMessageEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jobrunr.jobs.annotations.Job;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component; // 使用 Component 即可

import java.util.function.Consumer;

/**
 * 专门供 JobRunr 调用的任务执行代理
 * 它的作用是：JobRunr -> ShiroJobRunner -> BotJobHandler (你的业务逻辑)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ShiroJobRunner {

    private final BotContainer botContainer;
    private final ApplicationContext applicationContext;
    private final ObjectMapper objectMapper;
    @Lazy @Autowired
    private ShiroScheduleTaskService taskService;

    /**
     * 这才是 JobRunr 真正看见的入口
     */
    @Job(name = "HandlerJob: %0", retries = 5)
    public void runHandlerJob(Long botId, PushTargetType targetType, Long targetId,
                              Class<? extends BotJobHandler<?,? extends BasePluginConfig>> handlerClass,
                              Object parameter) {
        ShiroScheduleTask taskConfig = taskService.getTaskConfig(botId, targetType, targetId, handlerClass);
        if (taskConfig == null) {
            log.warn("任务 [{} - {}] 在数据库中不存在（可能已被删除），终止执行。",taskService.generateJobId(botId,targetType,targetId,taskService.resolveTaskKey(handlerClass)), targetId);
            return; // 直接返回，不抛异常，这样 JobRunr 就会认为任务成功完成（Succeeded）而不是失败重试
        }

        if (Boolean.FALSE.equals(taskConfig.getIsEnabled())) {
            log.info("任务 [{} - {}] 已禁用，本次调度跳过。", taskConfig.getTaskType(), targetId);
            return; // 同样直接返回，跳过执行
        }


        try {
            executeInternal(botId, targetType, targetId, handlerClass.getSimpleName(), bot -> {
                // 1. 获取 Handler 实例
                BotJobHandler<?,? extends BasePluginConfig> handler = getHandlerInstance(handlerClass);
                // 2. 参数转换
                Object typedParam = convertParameter(handler, parameter);
                // 3. 准备上下文数据
                // 构造虚拟 Event
                var virtualEvent = switch (targetType) {
                    case GROUP -> GroupMessageEvent.builder().selfId(botId).groupId(targetId).build();
                    case PRIVATE -> PrivateMessageEvent.builder().selfId(botId).userId(targetId).build();
                };


                // 获取 Handler 提供的 Config (利用默认方法，可能是 None)
                BasePluginConfig config = handler.getPluginConfig();

                // 4. 【核心】自动绑定 ScopedValue 并执行
                // 利用 BotContext 里我们之前写的辅助方法，或者直接在这里 where
                BotContext.runWithContext(bot, virtualEvent, config, () -> {
                    // 真正的业务逻辑在这里执行
                    // 此时 Scope 内已经有了 Bot, Event, Config
                    ((BotJobHandler<Object, ?>) handler).run(bot, targetId, targetType, typedParam);
                });
            });
        } catch (Exception e) {
            log.error("JobRunr任务执行失败: {}", handlerClass.getSimpleName(), e);
            throw e; // 抛出异常让 JobRunr 重试
        }
    }

    // --- 把原 Service 里那些 private 的辅助方法（executeInternal, convertParameter, getHandlerInstance）剪切到这里 ---

    /**
     * 内部执行包装器：负责 Bot 状态检查
     */
    private void executeInternal(Long botId, PushTargetType targetType, Long targetId,
                                 String taskName, Consumer<Bot> action) {
        Bot bot = botContainer.robots.get(botId);

        // 关键逻辑：如果 Bot 离线，抛出异常以触发 JobRunr 的指数退避重试
        if (bot == null) {
            String msg = String.format("Bot [%s] 离线，任务 [%s] 稍后重试", botId, taskName);
            log.warn(msg);
            throw new IllegalStateException(msg);
        }

        action.accept(bot);
    }

    /**
     * 参数类型转换器
     */
    private Object convertParameter(BotJobHandler<?,? extends BasePluginConfig> handler, Object rawParam) {
        if (rawParam == null) {
            return null;
        }

        // 1. 反射分析 Handler 的 run 方法参数类型
        Class<?> targetType = Object.class;
        for (var method : handler.getClass().getMethods()) {
            if ("run".equals(method.getName()) && method.getParameterCount() == 3) {
                // run(Bot bot, Long targetId, T parameter) -> 第三个参数下标是 2
                targetType = method.getParameterTypes()[2];
                break;
            }
        }

        // 2. 如果已经是目标类型，直接返回
        if (targetType.isInstance(rawParam)) {
            return rawParam;
        }

        // 3. 如果是 JSON 字符串，先尝试解析 (根据情况可选)
        // if (rawParam instanceof String strParam && !targetType.equals(String.class)) { ... }

        // 4. 使用 Jackson 进行 Convert (处理 Map -> DTO 的情况)
        try {
            return objectMapper.convertValue(rawParam, targetType);
        } catch (IllegalArgumentException e) {
            log.error("参数转换失败: 期望类型 {}, 实际值 {}", targetType.getSimpleName(), rawParam);
            throw e;
        }
    }

    /**
     * 获取 Handler 实例 (优先 Spring 容器，其次反射)
     */
    private BotJobHandler<?,? extends BasePluginConfig> getHandlerInstance(Class<? extends BotJobHandler<?, ? extends BasePluginConfig>> handlerClass) {
        try {
            return applicationContext.getBean(handlerClass);
        } catch (Exception e) {
            try {
                // 如果没有注册为 Bean，尝试手动实例化
                return handlerClass.getDeclaredConstructor().newInstance();
            } catch (Exception ex) {
                throw new RuntimeException("无法实例化 Handler: " + handlerClass.getName(), ex);
            }
        }
    }
}
