package com.github.winefoxbot.core.aitools;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Description;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.Locale;
import java.util.function.Function;

/**
 * 一个用于获取当前日期和时间的工具。
 * AI可以通过调用此工具来获知实时的时间信息。
 * @author FlanChan
 */
@Configuration(proxyBeanMethods = false)
@Slf4j
@RequiredArgsConstructor
public class CurrentTimeTool {

    /**
     * 获取当前详细时间信息，包括年份、月日、星期以及具体时间。
     *
     * @return 包含当前年份、日期（YYYY-MM-DD）、星期（中文）和时间（HH:mm:ss）的字符串。
     */
    @Bean("dateTimeTool")
    @Description("获取当前详细时间信息，包括年份、月日、星期以及具体时间。例如：2023年10月27日 星期五 15:30:00；你可以在任何需要查询时间的时候使用，不必返回原样信息给用户")
    public Function<Void,String> getCurrentDateTime() {
        return _ -> {
            // 获取当前日期
            LocalDate currentDate = LocalDate.now();
            // 获取当前时间
            LocalTime currentTime = LocalTime.now();
            // 获取当前是星期几
            DayOfWeek dayOfWeek = currentDate.getDayOfWeek();

            // 定义日期和时间的格式化器
            DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("yyyy年MM月dd日");
            DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss");

            // 将星期翻译成中文
            String dayOfWeekInChinese = dayOfWeek.getDisplayName(TextStyle.FULL, Locale.CHINESE);

            // 格式化日期和时间
            String formattedDate = currentDate.format(dateFormatter);
            String formattedTime = currentTime.format(timeFormatter);

            // 拼接最终的字符串
            return String.format("当前时间是：%s %s %s", formattedDate, dayOfWeekInChinese, formattedTime);
        };
    }
}
