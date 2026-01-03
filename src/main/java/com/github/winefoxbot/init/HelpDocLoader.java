package com.github.winefoxbot.init;

import com.github.winefoxbot.annotation.PluginFunction;
import com.github.winefoxbot.config.HelpDocConfiguration;
import com.github.winefoxbot.model.dto.helpdoc.HelpData;
import com.github.winefoxbot.model.dto.helpdoc.HelpDoc;
import com.github.winefoxbot.model.dto.helpdoc.HelpGroup;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.aop.support.AopUtils;
import org.springframework.context.ApplicationContext;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Component
@Slf4j
@RequiredArgsConstructor
public class HelpDocLoader {

    // [修改] 用于存储所有功能文档，按分组名聚合
    private final Map<String, List<HelpDoc>> groupedDocs = new ConcurrentHashMap<>();

    private final HelpDocConfiguration docConfiguration;

    @EventListener(ContextRefreshedEvent.class)
    public void onApplicationEvent(ContextRefreshedEvent event) {
        log.info("Spring context refreshed. Starting to load help documents...");
        ApplicationContext context = event.getApplicationContext();

        // 1. 清空旧数据
        groupedDocs.clear();

        // 2. 从 help-docs.json 加载基础文档
        HelpData configData = docConfiguration.getHelpData();
        if (configData != null && configData.getGroups() != null) {
            configData.getGroups().forEach(group -> {
                // 将JSON中定义的每个功能添加到map中
                group.getDocumentation().forEach(doc -> addDoc(group.getName(), doc));
            });
            log.info("Loaded {} group(s) from help-docs.json.", configData.getGroups().size());
        }

        // 3. 从注解和硬编码Bean加载，并合并到现有分组中
        loadHardcodedBeanDocs(context);
        scanForAnnotatedFunctions(context);

        log.info("Help documents loading finished. Total {} group(s) after merging.", groupedDocs.size());
    }


    private void addDoc(String groupName, HelpDoc doc) {
        groupedDocs.computeIfAbsent(groupName, k -> new ArrayList<>()).add(doc);
    }

    // [修改] 更新扫描逻辑以调用新的 addDoc
    private void scanForAnnotatedFunctions(ApplicationContext context) {
        String[] beanNames = context.getBeanDefinitionNames();
        for (String beanName : beanNames) {
            Object bean = context.getBean(beanName);
            Class<?> targetClass = AopUtils.getTargetClass(bean);

            for (Method method : targetClass.getDeclaredMethods()) {
                if (method.isAnnotationPresent(PluginFunction.class)) {
                    PluginFunction annotation = method.getAnnotation(PluginFunction.class);
                    if (annotation.hidden()) continue;

                    HelpDoc doc = new HelpDoc(
                            annotation.group(),
                            annotation.name(),
                            annotation.description(),
                            annotation.permission().getApplicableRolesDescription(),
                            List.of(annotation.commands())
                    );
                    // 使用注解中定义的group名
                    addDoc(annotation.group(), doc);
                }
            }
        }
    }

    // [修改] 更新硬编码Bean的加载逻辑
    private void loadHardcodedBeanDocs(ApplicationContext context) {
        // 注意：这种方式需要HelpDoc Bean自身能提供group信息，否则无法分组
        // 假设HelpDoc DTO没有group字段，此方法可能需要重新设计或废弃
        // 如果您的HelpDoc DTO仍然有group字段，那么原始逻辑仍然有效
        // 假设HelpDoc DTO没有group字段，这里注释掉，推荐使用注解或JSON
        /*
        Map<String, HelpDoc> helpDocBeans = context.getBeansOfType(HelpDoc.class);
        if (!helpDocBeans.isEmpty()) {
            log.warn("Hardcoded HelpDoc beans are found, but cannot be grouped without group info. Please use @PluginFunction or JSON config.");
        }
        */
    }

    /**
     * [核心方法] 提供给服务层调用，返回排序和结构化好的数据。
     * 这个方法现在是关键，它整合了所有数据源，并按照JSON配置进行排序。
     */
    public HelpData getSortedHelpData() {
        HelpData sourceData = docConfiguration.getHelpData();
        if (sourceData == null) {
            return new HelpData(); // 返回空对象防止NPE
        }

        // 1. 创建一个新的HelpData副本用于返回，避免修改原始配置对象
        HelpData finalData = new HelpData();
        finalData.setTopBackground(sourceData.getTopBackground());
        finalData.setBottomBackground(sourceData.getBottomBackground());
        finalData.setDefaultIcon(sourceData.getDefaultIcon());

        // 2. 获取JSON中定义的分组顺序和元数据
        List<HelpGroup> configuredGroups = sourceData.getGroups() != null ? sourceData.getGroups() : new ArrayList<>();

        // 3. 将所有加载的文档（包括注解扫描的）填充到这些分组中
        List<HelpGroup> resultGroups = configuredGroups.stream()
                .map(cg -> {
                    // 创建一个新的HelpGroup实例
                    HelpGroup newGroup = new HelpGroup();
                    newGroup.setName(cg.getName());
                    newGroup.setPriority(cg.getPriority());
                    newGroup.setIcon(cg.getIcon());
                    // 从groupedDocs中获取该分组的所有文档
                    newGroup.setDocumentation(groupedDocs.getOrDefault(cg.getName(), Collections.emptyList()));
                    return newGroup;
                })
                .collect(Collectors.toList());

        // 4. 处理那些通过注解添加，但在JSON中未定义的分组
        groupedDocs.keySet().forEach(groupName -> {
            boolean isConfigured = resultGroups.stream().anyMatch(g -> g.getName().equals(groupName));
            if (!isConfigured) {
                log.warn("Group '{}' found via annotation but not configured in help-docs.json. It will be appended to the end.", groupName);
                HelpGroup newGroup = new HelpGroup();
                newGroup.setName(groupName);
                newGroup.setPriority(Integer.MAX_VALUE); // 放在最后
                newGroup.setIcon(null); // 将使用默认图标
                newGroup.setDocumentation(groupedDocs.get(groupName));
                resultGroups.add(newGroup);
            }
        });

        // 5. 对最终结果进行排序
        resultGroups.sort(Comparator.comparingInt(HelpGroup::getPriority));

        finalData.setGroups(resultGroups);
        return finalData;
    }

    // [保留] 如果还有地方需要原始的Map，可以保留这个方法
    public Map<String, List<HelpDoc>> getGroupedDocs() {
        return Collections.unmodifiableMap(groupedDocs);
    }
}
