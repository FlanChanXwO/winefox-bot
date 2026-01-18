package com.github.winefoxbot.core.controller;

import com.github.winefoxbot.core.model.vo.webui.resp.ConfigItemResponse;
import com.github.winefoxbot.core.service.webui.WebUIConfigDashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * @author FlanChan
 */
@RestController
@RequestMapping("/api/system/config")
@RequiredArgsConstructor
public class WebUISystemConfigController {

    private final WebUIConfigDashboardService configService;

    @GetMapping
    public List<ConfigItemResponse> getConfig() {
        // Result 是你统一的返回包装类
        return configService.getAllConfigs();
    }
}
