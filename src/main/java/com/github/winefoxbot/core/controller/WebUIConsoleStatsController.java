package com.github.winefoxbot.core.controller;

import com.github.winefoxbot.core.model.vo.webui.resp.ConsoleStatsResponse;
import com.github.winefoxbot.core.service.webui.WebUIStatsService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/console")
@RequiredArgsConstructor
public class WebUIConsoleStatsController {

    private final WebUIStatsService statsService;

    /**
     * 获取首页仪表盘统计数据
     *
     * @return 统计数据 Record
     */
    @GetMapping("/stats")
    public ConsoleStatsResponse getConsoleStats() {
        return statsService.getConsoleStats();
    }
}
