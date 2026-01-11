package com.github.winefoxbot.plugins.dailyreport.config;

import com.github.winefoxbot.plugins.dailyreport.model.enums.HolidayRegion;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * @author FlanChan
 */
@Data
@ConfigurationProperties(prefix = "daily-report")
public class DailyReportProperties {
    private String alapiUrl = "https://60s.viki.moe/v2/60s";
    private String templatePath = "daily_report/main";
    private HolidayRegion holidayRegion = HolidayRegion.CN;
}
