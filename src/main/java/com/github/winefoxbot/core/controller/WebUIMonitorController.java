package com.github.winefoxbot.core.controller;


import com.github.winefoxbot.core.model.vo.webui.resp.SystemStatusResponse;
import com.github.winefoxbot.core.service.webui.WebUISystemMonitorService;
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
    private WebUISystemMonitorService monitorService;

    @GetMapping("/status")
    public SystemStatusResponse getStatus() {
        return monitorService.getSystemStatus();
    }
}
