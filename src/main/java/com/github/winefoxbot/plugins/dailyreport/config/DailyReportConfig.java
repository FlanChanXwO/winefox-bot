package com.github.winefoxbot.plugins.dailyreport.config;

import com.github.winefoxbot.plugins.dailyreport.model.enums.HolidayRegion;
import com.github.winefoxbot.plugins.dailyreport.service.HolidayService;
import com.github.winefoxbot.plugins.dailyreport.service.impl.ChineseHolidayServiceImpl;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Objects;

/**
 * @author FlanChan (badapple495@outlook.com)
 * @since 2026-01-11-7:48
 */
@Configuration
@EnableConfigurationProperties(DailyReportProperties.class)
public class DailyReportConfig {

    @Bean
    public HolidayService holidayService(DailyReportProperties properties) {
        if (Objects.requireNonNull(properties.getHolidayRegion()) == HolidayRegion.CN) {
            return new ChineseHolidayServiceImpl();
        }
        throw new IllegalArgumentException("Unknown holiday region: " + properties.getHolidayRegion());
    }
}