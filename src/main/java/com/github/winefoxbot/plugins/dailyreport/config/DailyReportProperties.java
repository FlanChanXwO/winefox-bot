package com.github.winefoxbot.plugins.dailyreport.config;

import com.github.winefoxbot.plugins.dailyreport.model.enums.HolidayRegion;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @author FlanChan
 */
@Data
@ConfigurationProperties(prefix = "winefoxbot.plugins.dailyreport")
public class DailyReportProperties {
    private String alapiUrl = "https://60s.viki.moe/v2/60s";

    private HolidayRegion holidayRegion = HolidayRegion.CN;

    private String preGenerateCron = "0 30 7 * * ?";

}
