package com.github.winefoxbot.core.service.schedule;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.winefoxbot.core.annotation.plugin.Plugin;
import com.github.winefoxbot.core.config.plugin.BasePluginConfig;
import com.github.winefoxbot.core.context.BotContext;
import com.github.winefoxbot.core.model.entity.ShiroScheduleTask;
import com.github.winefoxbot.core.model.enums.common.PushTargetType;
import com.github.winefoxbot.core.service.plugin.PluginService;
import com.github.winefoxbot.core.service.schedule.handler.BotJobHandler;
import com.github.winefoxbot.core.util.PluginConfigBinder;
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
import org.springframework.stereotype.Component;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
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
    private final PluginConfigBinder configBinder;
    private final PluginService pluginService;
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

        // 检查插件全局开关
        if (handlerClass.isAnnotationPresent(Plugin.class)) {
            Plugin pluginAnno = handlerClass.getAnnotation(Plugin.class);
            String pluginId = handlerClass.getSimpleName();
            if (!pluginService.getPluginEnabledStatus(pluginId)) {
                log.info("插件 [{}] (ID: {}) 全局开关已关闭，任务终止。", pluginAnno.name(), pluginId);
                return;
            }
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


                // 获取 Config
                BasePluginConfig config;
                if (handlerClass.isAnnotationPresent(Plugin.class)) {
                    // 优先使用 @Plugin 注解定义的 Config Class，确保每次都是新实例
                    Class<? extends BasePluginConfig> configClass = handlerClass.getAnnotation(Plugin.class).config();
                    if (configClass == BasePluginConfig.None.class) {
                        config = new BasePluginConfig.None();
                    } else {
                        try {
                            config = configClass.getDeclaredConstructor().newInstance();
                        } catch (Exception e) {
                            throw new RuntimeException("无法实例化插件配置: " + configClass.getName(), e);
                        }
                    }
                } else {
                    // 尝试通过泛型获取 Config Class
                    Class<? extends BasePluginConfig> configClass = getGenericConfigClass(handlerClass);
                    if (configClass != null && configClass != BasePluginConfig.None.class) {
                        try {
                            config = configClass.getDeclaredConstructor().newInstance();
                        } catch (Exception e) {
                            // 无法实例化时，使用 None
                            config = new BasePluginConfig.None();
                        }
                    } else {
                        // 无法获取泛型时，使用 None
                        config = new BasePluginConfig.None();
                    }
                }

                if (!(config instanceof BasePluginConfig.None)) {
                    configBinder.bind(config, targetType == PushTargetType.GROUP ? targetId : null, targetType == PushTargetType.PRIVATE ? targetId : null);
                }

                // 4. 【核心】自动绑定 ScopedValue 并执行
                // 利用 BotContext 里我们之前写的辅助方法，或者直接在这里 where
                BotContext.runWithContext(bot, virtualEvent, config, () -> {
                    // 真正的业务逻辑在这里执行
                    // 此时 Scope 内已经有了 Bot, Event, Config
                    ((BotJobHandler<Object, ?>) handler).run(bot, targetId, targetType, typedParam);
                });
            }, handlerClass);
        } catch (Exception e) {
            log.error("JobRunr任务执行失败: {}", handlerClass.getSimpleName(), e);
            throw e; // 抛出异常让 JobRunr 重试
        }
    }

    /**
     * 尝试从 BotJobHandler 实现类的泛型中解析出 Config 的类型
     */
    @SuppressWarnings("unchecked")
    private Class<? extends BasePluginConfig> getGenericConfigClass(Class<?> handlerClass) {
        // 遍历 handlerClass 实现的所有接口
        for (Type genericInterface : handlerClass.getGenericInterfaces()) {
            if (genericInterface instanceof ParameterizedType parameterizedType) {
                // 检查是否是 BotJobHandler 接口
                if (parameterizedType.getRawType().equals(BotJobHandler.class)) {
                    // BotJobHandler<P, C> -> 第二个泛型参数是 Config
                    Type[] typeArguments = parameterizedType.getActualTypeArguments();
                    if (typeArguments.length >= 2) {
                        Type configType = typeArguments[1];
                        if (configType instanceof Class<?> clazz && BasePluginConfig.class.isAssignableFrom(clazz)) {
                            return (Class<? extends BasePluginConfig>) clazz;
                        }
                    }
                }
            }
        }
        return null;
    }

    // --- 把原 Service 里那些 private 的辅助方法（executeInternal, convertParameter, getHandlerInstance）剪切到这里 ---

    /**
     * 内部执行包装器：负责 Bot 状态检查
     */
    private void executeInternal(Long botId, PushTargetType targetType, Long targetId,
                                 String taskName, Consumer<Bot> action, Class<? extends BotJobHandler<?, ? extends BasePluginConfig>> handlerClass) {
        Bot bot = botContainer.robots.get(botId);

        // 关键逻辑：如果 Bot 离线，抛出异常以触发 JobRunr 的指数退避重试
        if (bot == null) {
            String msg = String.format("Bot [%s] 离线，任务 [%s] 稍后重试", botId, taskName);
            log.warn(msg);
            throw new IllegalStateException(msg);
        }

        // 检查目标是否存在
        boolean targetExists = false;
        if (targetType == PushTargetType.GROUP) {
            targetExists = bot.getGroupList().getData().stream().anyMatch(g -> g.getGroupId().equals(targetId));
        } else if (targetType == PushTargetType.PRIVATE) {
            // 私聊用户检查比较复杂，这里简单检查好友列表，或者假设存在
            // 如果需要严格检查，可以遍历好友列表
             targetExists = bot.getFriendList().getData().stream().anyMatch(f -> f.getUserId().equals(targetId));
        }

        if (!targetExists) {
            log.warn("目标 [{} - {}] 不存在，尝试删除任务并终止。", targetType, targetId);
            // 删除任务
            taskService.deleteTask(botId, targetType, targetId, taskService.resolveTaskKey(handlerClass));
            return;
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
            if ("run".equals(method.getName()) && method.getParameterCount() == 4) {
                // run(Bot bot, Long targetId, PushTargetType targetType, P parameter) -> 第四个参数下标是 3
                targetType = method.getParameterTypes()[3];
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
