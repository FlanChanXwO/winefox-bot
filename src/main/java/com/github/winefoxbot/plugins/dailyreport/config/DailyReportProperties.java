package com.github.winefoxbot.plugins.dailyreport.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * @author FlanChan
 */
@Data
@Component
@ConfigurationProperties(prefix = "daily-report")
public class DailyReportProperties {
    private String alapiUrl = "https://60s.viki.moe/v2/60s";
    private String templatePath = "daily_report/main";
}
