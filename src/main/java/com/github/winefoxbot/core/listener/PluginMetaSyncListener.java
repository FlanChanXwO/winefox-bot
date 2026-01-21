package com.github.winefoxbot.core.listener;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.github.winefoxbot.core.annotation.plugin.Plugin;
import com.github.winefoxbot.core.model.entity.WinefoxBotPluginInvokeStats;
import com.github.winefoxbot.core.model.entity.WinefoxBotPluginMeta;
import com.github.winefoxbot.core.service.plugin.WinefoxBotPluginInvokeStatsService;
import com.github.winefoxbot.core.service.plugin.WinefoxBotPluginMetaService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.aop.support.AopUtils;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationContext;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @author FlanChan
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class PluginMetaSyncListener {

    private final ApplicationContext applicationContext;
    private final WinefoxBotPluginMetaService metaService;
    private final WinefoxBotPluginInvokeStatsService statsService;

    /**
     * 应用完全启动后执行
     * 同步当前存在的插件状态
     */
    @EventListener(ApplicationReadyEvent.class)
    public void syncPluginStatus() {
        log.info("正在同步插件元数据...");

        // 1. 获取当前代码中【正在运行】的所有插件 ClassName
        Set<String> activeClassNames = applicationContext.getBeansWithAnnotation(Plugin.class).values().stream()
                .map(bean -> AopUtils.getTargetClass(bean).getName())
                .collect(Collectors.toSet());

        // 2. 获取数据库中【已存储】的所有插件 ClassName
        // 只查主键列，提高性能
        List<String> dbClassNames = metaService.list(
                new LambdaQueryWrapper<WinefoxBotPluginMeta>().select(WinefoxBotPluginMeta::getClassName)
        ).stream().map(WinefoxBotPluginMeta::getClassName).toList();

        // 3. 计算差集：在数据库中存在，但代码里没了，就是需要删除的
        List<String> toDeleteClassNames = dbClassNames.stream()
                .filter(name -> !activeClassNames.contains(name))
                .collect(Collectors.toList());

        if (toDeleteClassNames.isEmpty()) {
            return;
        }

        log.warn("检测到 {} 个无效插件，准备删除: {}", toDeleteClassNames.size(), toDeleteClassNames);

        // 4. 执行物理删除（顺序很重要！）

        // 4.1 先删除【统计表】中的关联数据 (解除外键依赖)
        statsService.remove(
                new LambdaUpdateWrapper<WinefoxBotPluginInvokeStats>()
                        .in(WinefoxBotPluginInvokeStats::getPluginClassName, toDeleteClassNames)
        );

        // 4.2 再删除【元数据表】中的数据
        metaService.removeByIds(toDeleteClassNames);

        if (!toDeleteClassNames.isEmpty()) {
            log.info("清理完成。移除了 {} 个插件及其所有历史统计数据。", toDeleteClassNames.size());
        }
    }
}
