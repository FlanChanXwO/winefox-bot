package com.github.winefoxbot.core.init;

import com.github.winefoxbot.core.annotation.plugin.Plugin;
import com.github.winefoxbot.core.annotation.plugin.PluginFunction;
import com.github.winefoxbot.core.config.helpdoc.HelpDocConfiguration;
import com.github.winefoxbot.core.model.dto.HelpData;
import com.github.winefoxbot.core.model.dto.HelpDoc;
import com.github.winefoxbot.core.model.dto.HelpGroup;
import com.github.winefoxbot.core.model.enums.common.Permission;
import com.github.winefoxbot.core.utils.CommandRegexParser;
import com.mikuac.shiro.annotation.AnyMessageHandler;
import com.mikuac.shiro.annotation.GroupMessageHandler;
import com.mikuac.shiro.annotation.MessageHandlerFilter;
import com.mikuac.shiro.annotation.PrivateMessageHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.aop.support.AopUtils;
import org.springframework.context.ApplicationContext;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Component
@Slf4j
@RequiredArgsConstructor
public class HelpDocLoader {

    private final Map<String, HelpGroup> helpGroups = new ConcurrentHashMap<>();
    private final HelpDocConfiguration docConfiguration;

    @EventListener(ContextRefreshedEvent.class)
    public void onApplicationEvent(ContextRefreshedEvent event) {
        log.info("Spring context refreshed. Starting to load help documents...");
        ApplicationContext context = event.getApplicationContext();

        helpGroups.clear();
        loadGroupsFromConfig();
        scanForAnnotatedPlugins(context);

        log.info("Help documents loading finished. Total {} group(s) loaded.", helpGroups.size());
    }

    private void loadGroupsFromConfig() {
        HelpData configData = docConfiguration.getHelpData();
        if (configData != null && configData.getGroups() != null) {
            configData.getGroups().forEach(group -> {
                if (group.getDocumentation() != null && !group.getDocumentation().isEmpty()) {
                    // 确保 documentation 列表是可变的
                    group.setDocumentation(group.getDocumentation());
                    helpGroups.put(group.getName(), group);
                }

            });
            log.info("Loaded {} group(s) metadata from help-docs.json.", configData.getGroups().size());
        }
    }

    private void scanForAnnotatedPlugins(ApplicationContext context) {
        Map<String, Object> beansWithAnnotation = context.getBeansWithAnnotation(Plugin.class);

        for (Object bean : beansWithAnnotation.values()) {
            Class<?> targetClass = AopUtils.getTargetClass(bean);
            if (!targetClass.isAnnotationPresent(Plugin.class)) continue;

            Plugin pluginAnnotation = targetClass.getAnnotation(Plugin.class);
            if (pluginAnnotation.hidden()) continue;

            String groupName = pluginAnnotation.name();

            // 1. 获取或创建 HelpGroup
            HelpGroup group = helpGroups.computeIfAbsent(groupName, k -> {
                log.debug("Group '{}' not found in config, creating new one from annotation on class '{}'.", groupName, targetClass.getSimpleName());
                HelpGroup newGroup = new HelpGroup();
                newGroup.setName(groupName);
                newGroup.setDocumentation(new ArrayList<>());
                return newGroup;
            });

            // 2. [核心改动] 智能合并元数据
            mergeGroupMetadata(group, pluginAnnotation, targetClass);

            // 3. 遍历方法，加载功能文档并添加到合并后的Group中
            for (Method method : targetClass.getDeclaredMethods()) {
                if (method.isAnnotationPresent(PluginFunction.class) && isPresentMessageHandlerAnnotation(method)) {
                    PluginFunction funcAnnotation = method.getAnnotation(PluginFunction.class);
                    if (funcAnnotation.hidden()) continue;

                    HelpDoc helpDoc = new HelpDoc();
                    helpDoc.setName(funcAnnotation.name());
                    helpDoc.setDescription(funcAnnotation.description());

                    // 注解的 permission 优先于类的 permission
                    Permission finalPermission = funcAnnotation.permission() == Permission.USER ?
                            pluginAnnotation.permission() : funcAnnotation.permission();
                    helpDoc.setPermission(finalPermission.getApplicableRolesDescription());

                    List<String> commands = new ArrayList<>(Arrays.asList(funcAnnotation.commands()));
                    if (funcAnnotation.autoGenerateHelp()) {
                        String cmdRegex = getCmdRegexFromMessageHandlerAnnotation(method);
                        if (cmdRegex != null) {
                            commands.addAll(CommandRegexParser.generateCommands(cmdRegex));
                        }
                    }
                    helpDoc.setCommands(commands.stream().distinct().collect(Collectors.toList()));

                    group.getDocumentation().add(helpDoc);
                }
            }
        }
    }

    /**
     * [新增] 智能合并来自 @Plugin 注解的元数据到现有的 HelpGroup 对象中。
     *
     * @param existingGroup    已存在的 HelpGroup 对象（可能来自JSON或之前的注解）
     * @param pluginAnnotation 当前处理的 @Plugin 注解
     * @param sourceClass      注解所在的类，用于日志记录
     */
    private void mergeGroupMetadata(HelpGroup existingGroup, Plugin pluginAnnotation, Class<?> sourceClass) {
        // 规则1: order 总是取优先级最高的（值最小的）
        if (pluginAnnotation.order() < existingGroup.getOrder()) {
            log.debug("Updating group '{}' order from {} to {} (from class {}).",
                    existingGroup.getName(), existingGroup.getOrder(), pluginAnnotation.order(), sourceClass.getSimpleName());
            existingGroup.setOrder(pluginAnnotation.order());
        }

        // 规则2 (已修复): 如果注解中定义了 icon, 就用注解的，无视 order 和现有值
        if (StringUtils.hasText(pluginAnnotation.iconPath())) {
            if (!pluginAnnotation.iconPath().equals(existingGroup.getIcon())) { // 仅在值不同时打印日志
                log.debug("Updating group '{}' icon from '{}' to '{}' (from @Plugin on class {}).",
                        existingGroup.getName(), existingGroup.getIcon(), pluginAnnotation.iconPath(), sourceClass.getSimpleName());
            }
            existingGroup.setIcon(pluginAnnotation.iconPath());
        }
    }



    private String getCmdRegexFromMessageHandlerAnnotation(Method method) {
        if (method.isAnnotationPresent(MessageHandlerFilter.class)) {
            MessageHandlerFilter filter = method.getAnnotation(MessageHandlerFilter.class);
            return filter.cmd();
        }
        return null;
    }

    private boolean isPresentMessageHandlerAnnotation(Method method) {
        return method.isAnnotationPresent(AnyMessageHandler.class) ||
                method.isAnnotationPresent(PrivateMessageHandler.class) ||
                method.isAnnotationPresent(GroupMessageHandler.class);
    }

    public HelpData getSortedHelpData() {
        HelpData data = new HelpData();
        List<HelpGroup> sortedGroups = helpGroups.values().stream()
                // 过滤掉没有实际功能文档的分组（解决空类生成空分组的问题）
                .filter(group -> group.getDocumentation() != null && !group.getDocumentation().isEmpty())
                .peek(group -> {
                    // 在最后环节设置默认图标
                    if (!StringUtils.hasText(group.getIcon())) {
                        group.setIcon(data.getDefaultIcon());
                    }
                })
                .sorted(Comparator.comparingInt(HelpGroup::getOrder))
                .collect(Collectors.toList());

        data.setGroups(sortedGroups);
        return data;
    }

}
