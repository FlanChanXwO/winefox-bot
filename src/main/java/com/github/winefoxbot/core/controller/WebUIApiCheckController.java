package com.github.winefoxbot.core.controller;

import com.github.winefoxbot.core.config.app.WineFoxBotAppProperties;
import com.github.winefoxbot.core.model.vo.webui.resp.HealthStatusResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 让前端可以通过这个接口检查后端服务是否在线
 *
 * @author FlanChan (badapple495@outlook.com)
 * @since 2026-01-18-16:53
 */
@RestController
@RequestMapping("/api/check")
@RequiredArgsConstructor
public class WebUIApiCheckController {

    private final WineFoxBotAppProperties properties;

    @GetMapping("/ping")
    public HealthStatusResponse ping() {
        return new HealthStatusResponse(
                "winefox-bot",
                properties.getVersion()
        );
    }

}