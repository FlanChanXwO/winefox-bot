// src/main/java/com/github/winefoxbot/config/properties/StatusImageGeneratorProperties.java
package com.github.winefoxbot.core.config.status;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * @author FlanChan
 */
@Data
@Component
@ConfigurationProperties(prefix = "winefoxbot.plugins.status")
public class StatusImageGeneratorProperties {

    /**
     * Bot 的名称
     */
    private String botName = "WineFoxBot";

    /**
     * 项目名称
     */
    private String projectName ="WineFoxBot";

    /**
     * Dashboard 的名称
     */
    private String dashboardName = "WineFoxBot Dashboard";

}
