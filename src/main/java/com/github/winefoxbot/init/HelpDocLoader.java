package com.github.winefoxbot.init;

import com.github.winefoxbot.annotation.PluginFunction;
import com.github.winefoxbot.config.HelpDocConfiguration;
import com.github.winefoxbot.model.dto.helpdoc.HelpDoc;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.aop.support.AopUtils;
import org.springframework.context.ApplicationContext;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Slf4j
@RequiredArgsConstructor
public class HelpDocLoader {


    private final Map<String, List<HelpDoc>> groupedDocs = new ConcurrentHashMap<>();

    private final HelpDocConfiguration docConfiguration;

    /**
     * 监听 ContextRefreshedEvent 事件。
     * 这个事件在 Spring 容器初始化或刷新完成后发布，确保所有 Bean 都已可用。
     * 这是执行扫描的最佳时机。
     */
    @EventListener(ContextRefreshedEvent.class)
    public void onApplicationEvent(ContextRefreshedEvent event) {
        log.info("Spring context refreshed. Starting to load help documents...");

        // 1. 获取 ApplicationContext
        ApplicationContext context = event.getApplicationContext();

        // 2. 清空旧数据，以支持热重载场景
        groupedDocs.clear();

        // 3. 从配置文件加载帮助文档
        docConfiguration.getDocumentation().forEach(this::addDoc);

        // 4. 从 ApplicationContext 中加载所有 HelpDoc 类型的 Bean (用于硬编码)
        loadHardcodedBeanDocs(context);

        // 5. 扫描所有 Bean，查找 @PluginFunction 注解
        scanForAnnotatedFunctions(context);

        log.info("Help documents loading finished. Found {} group(s).", groupedDocs.size());
    }

    private void scanForAnnotatedFunctions(ApplicationContext context) {
        // 获取容器中所有的 Bean 名称
        String[] beanNames = context.getBeanDefinitionNames();
        for (String beanName : beanNames) {
            Object bean = context.getBean(beanName);
            // 使用 AopUtils.getTargetClass 获取原始类，以防 Bean 是 AOP 代理对象
            Class<?> targetClass = AopUtils.getTargetClass(bean);

            for (Method method : targetClass.getDeclaredMethods()) {
                if (method.isAnnotationPresent(PluginFunction.class)) {
                    PluginFunction annotation = method.getAnnotation(PluginFunction.class);
                    HelpDoc doc = new HelpDoc(
                            annotation.group(),
                            annotation.name(),
                            annotation.description(),
                            annotation.permission(),
                            List.of(annotation.commands())
                    );
                    addDoc(doc);
                }
            }
        }
    }

    private void loadHardcodedBeanDocs(ApplicationContext context) {
        // 查找所有类型为 HelpDoc 的 Bean
        Map<String, HelpDoc> helpDocBeans = context.getBeansOfType(HelpDoc.class);
        if (!helpDocBeans.isEmpty()) {
            log.info("Found {} hardcoded HelpDoc bean(s).", helpDocBeans.size());
            helpDocBeans.values().forEach(this::addDoc);
        }
    }

    private void addDoc(HelpDoc doc) {
        groupedDocs.computeIfAbsent(doc.getGroup(), k -> new ArrayList<>()).add(doc);
    }

    /**
     * 公共接口，用于让其他服务获取已加载的帮助文档。
     * 返回一个不可修改的 Map 视图，保证数据安全。
     */
    public Map<String, List<HelpDoc>> getGroupedDocs() {
        return Collections.unmodifiableMap(groupedDocs);
    }

}
