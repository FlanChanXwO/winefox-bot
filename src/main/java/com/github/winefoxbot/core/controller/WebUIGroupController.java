package com.github.winefoxbot.core.controller;

import com.github.winefoxbot.core.model.vo.webui.resp.GroupStatsResponse;
import com.github.winefoxbot.core.service.webui.WebUIStatsService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * @author FlanChan (badapple495@outlook.com)
 * @since 2026-01-18-21:18
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/group")
public class WebUIGroupController {
    private final WebUIStatsService statsService;


    @GetMapping("/stats")
    public GroupStatsResponse getGroupIdList() {
        return statsService.getActiveGroupStats();
    }
}