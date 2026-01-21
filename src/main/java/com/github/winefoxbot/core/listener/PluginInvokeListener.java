package com.github.winefoxbot.core.listener;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.github.winefoxbot.core.annotation.plugin.Plugin;
import com.github.winefoxbot.core.event.PluginCalledEvent;
import com.github.winefoxbot.core.model.entity.WinefoxBotPluginInvokeStats;
import com.github.winefoxbot.core.model.entity.WinefoxBotPluginMeta;
import com.github.winefoxbot.core.service.plugin.WinefoxBotPluginInvokeStatsService;
import com.github.winefoxbot.core.service.plugin.WinefoxBotPluginMetaService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Component
@Slf4j
@RequiredArgsConstructor
public class PluginInvokeListener {

    private final WinefoxBotPluginMetaService metaService;
    private final WinefoxBotPluginInvokeStatsService statsService;

    @Async
    @EventListener
    @Transactional(rollbackFor = Exception.class)
    public void handlePluginCall(PluginCalledEvent event) {
        Plugin pluginInfo = event.getPluginInfo();
        String className = event.getClassName();
        LocalDate today = LocalDate.now();

        // ---------------------------------------------------------
        // 1. 更新或保存插件元数据
        // ---------------------------------------------------------
        WinefoxBotPluginMeta meta = new WinefoxBotPluginMeta();
        meta.setClassName(className);
        meta.setDisplayName(pluginInfo.name());
        meta.setIconPath(pluginInfo.iconPath());
        meta.setIsActive(true);

        // MP 的 saveOrUpdate 会根据主键判断是否存在，不存在insert，存在update
        metaService.saveOrUpdate(meta);

        // ---------------------------------------------------------
        // 2. 更新统计数据 (原子递增)
        // ---------------------------------------------------------

        // 尝试直接更新：利用 setSql 实现数据库层面的原子 +1
        boolean updateSuccess = statsService.update(new LambdaUpdateWrapper<WinefoxBotPluginInvokeStats>()
                .eq(WinefoxBotPluginInvokeStats::getStatDate, today)
                .eq(WinefoxBotPluginInvokeStats::getPluginClassName, className)
                .setSql("call_count = call_count + 1")
        );

        // 如果更新失败（说明今天还没有这条记录），则进行插入
        if (!updateSuccess) {
            WinefoxBotPluginInvokeStats newStats = new WinefoxBotPluginInvokeStats();
            newStats.setStatDate(today);
            newStats.setPluginClassName(className);
            newStats.setCallCount(1L);

            try {
                statsService.save(newStats);
            } catch (DuplicateKeyException e) {
                // 极端并发情况：在 update 失败后、save 之前，另一个线程刚好插入了记录
                // 此时捕获唯一索引冲突，重新执行一次原子更新即可
                statsService.update(new LambdaUpdateWrapper<WinefoxBotPluginInvokeStats>()
                        .eq(WinefoxBotPluginInvokeStats::getStatDate, today)
                        .eq(WinefoxBotPluginInvokeStats::getPluginClassName, className)
                        .setSql("call_count = call_count + 1")
                );
            }
        }
    }
}
