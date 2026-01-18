package com.github.winefoxbot.core.controller;


import com.github.winefoxbot.core.model.dto.webui.SystemStatusDTO;
import com.github.winefoxbot.core.service.webui.SystemMonitorService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author FlanChan
 */
@RestController
@RequestMapping("/api/monitor")
public class WebUIMonitorController {

    @Autowired
    private SystemMonitorService monitorService;

    @GetMapping("/status")
    public SystemStatusDTO getStatus() {
        return monitorService.getSystemStatus();
    }
}
