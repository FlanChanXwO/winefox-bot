package com.github.winefoxbot.core.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.github.winefoxbot.core.model.entity.WinefoxBotConnectionLogs;
import com.github.winefoxbot.core.model.vo.common.Result;
import com.github.winefoxbot.core.service.connectionlogs.WinefoxBotConnectionLogsService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

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
    public Result<Page<WinefoxBotConnectionLogs>> getConnectionLogs(
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
        Page<WinefoxBotConnectionLogs> result = logsService.page(pageParam, wrapper);

        return Result.success(result);
    }
}