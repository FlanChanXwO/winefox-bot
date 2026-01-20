package com.github.winefoxbot.core.init;

import com.github.winefoxbot.core.annotation.schedule.BotTask;
import com.github.winefoxbot.core.model.vo.webui.resp.TaskTypeResponse;
import com.github.winefoxbot.core.service.schedule.handler.BotJobHandler;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 任务类型注册中心
 * 负责维护 Key <-> Handler Class 的映射关系
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BotTaskRegistry {

    // 自动注入所有实现了 BotJobHandler 的 Bean
    private final List<BotJobHandler<?>> handlerBeans;

    // Key -> Handler Class 的映射
    private final Map<String, Class<? extends BotJobHandler<?>>> keyToClassMap = new ConcurrentHashMap<>();

    // Class -> Metadata 的映射
    private final Map<Class<?>, BotTask> classToMetaMap = new ConcurrentHashMap<>();

    // 给前端用的列表缓存
    @Getter
    private final List<TaskTypeResponse> availableTasks = new ArrayList<>();

    @PostConstruct
    public void init() {
        for (BotJobHandler<?> handler : handlerBeans) {
            Class<?> clazz = handler.getClass();
            // 获取注解
            BotTask annotation = clazz.getAnnotation(BotTask.class);

            // 如果没有注解，跳过（或者你可以选择兼容旧逻辑）
            if (annotation == null) {
                log.warn("Handler [{}] 未添加 @BotTask 注解，将无法在 WebUI 中选择", clazz.getSimpleName());
                continue;
            }

            if (keyToClassMap.containsKey(annotation.key())) {
                throw new IllegalStateException("重复的 BotTask Key: " + annotation.key());
            }

            // 注册映射 (这里进行了强制类型转换，因为我们知道 handlerBeans 里的都是 BotJobHandler)
            @SuppressWarnings("unchecked")
            Class<? extends BotJobHandler<?>> handlerClass = (Class<? extends BotJobHandler<?>>) clazz;

            keyToClassMap.put(annotation.key(), handlerClass);
            classToMetaMap.put(clazz, annotation);

            // 添加到前端展示列表
            availableTasks.add(new TaskTypeResponse(
                    annotation.key(),
                    annotation.name(),
                    annotation.targetType(),
                    annotation.description(),
                    annotation.paramExample()
            ));
        }
        log.info("已加载 {} 个自动调度任务类型", availableTasks.size());
    }

    /**
     * 根据 Key 获取 Handler 类
     */
    public Class<? extends BotJobHandler<?>> getClassByKey(String key) {
        return keyToClassMap.get(key);
    }

    /**
     * 根据 类 获取 元数据
     */
    public BotTask getMetaByClass(Class<?> clazz) {
        return classToMetaMap.get(clazz);
    }

}
