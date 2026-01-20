package com.github.winefoxbot.core.controller;

import cn.hutool.core.date.DateUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.github.winefoxbot.core.model.entity.WinefoxBotConnectionLogs;
import com.github.winefoxbot.core.model.enums.ConnectionEventType;
import com.github.winefoxbot.core.model.vo.webui.resp.ConnectionSummaryResponse;
import com.github.winefoxbot.core.service.connectionlogs.WinefoxBotConnectionLogsService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * @author FlanChan (badapple495@outlook.com)
 * @since 2026-01-18-20:07
 */
@RestController
@RequestMapping("/api/logs")
@RequiredArgsConstructor
public class WebUIConnectionLogController {
    private final WinefoxBotConnectionLogsService logsService;

    /**
     * 分页查询连接日志
     * @param page 当前页 (默认1)
     * @param size 每页大小 (默认5，因为前端卡片比较小)
     */
    @GetMapping("/connection")
    public Page<WinefoxBotConnectionLogs> getConnectionLogs(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "5") int size,
            @RequestParam(required = true) Long botId
    ) {
        // 1. 构建分页对象
        Page<WinefoxBotConnectionLogs> pageParam = new Page<>(page, size);

        // 2. 构建查询条件：按创建时间倒序
        LambdaQueryWrapper<WinefoxBotConnectionLogs> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(WinefoxBotConnectionLogs::getBotId,botId);
        wrapper.orderByDesc(WinefoxBotConnectionLogs::getCreatedAt);
        // 3. 查询
        return logsService.page(pageParam, wrapper);
    }


    /**
     * 获取连接统计概览
     * 对应 UI：累计登录/连接时长/连接日期 面板
     */
    @GetMapping("/summary")
    public ConnectionSummaryResponse getConnectionSummary(@RequestParam Long botId) {
        // 1. 累计登录次数 (EventType = CONNECT)
        long loginCount = logsService.count(new LambdaQueryWrapper<WinefoxBotConnectionLogs>()
                .eq(WinefoxBotConnectionLogs::getBotId, botId)
                .eq(WinefoxBotConnectionLogs::getEventType, ConnectionEventType.CONNECT));

        // 2. 获取最新的一条日志，用于判断当前状态和计算时长
        WinefoxBotConnectionLogs lastLog = logsService.getOne(new LambdaQueryWrapper<WinefoxBotConnectionLogs>()
                .eq(WinefoxBotConnectionLogs::getBotId, botId)
                .orderByDesc(WinefoxBotConnectionLogs::getCreatedAt)
                .last("LIMIT 1")); // 只要最新的一条

        String durationStr = "00:00:00";
        LocalDateTime connectDate = null;

        if (lastLog != null && lastLog.getEventType() == ConnectionEventType.CONNECT) {
            // 如果最后一条是 CONNECT，说明当前在线
            connectDate = lastLog.getCreatedAt();
            Duration duration = Duration.between(connectDate, LocalDateTime.now());
            durationStr = DateUtil.secondToTime((int) duration.getSeconds());
        } else if (lastLog != null) {
             connectDate = lastLog.getCreatedAt();
        }

        return new ConnectionSummaryResponse(
                loginCount,
                durationStr,
                connectDate
        );
    }
}