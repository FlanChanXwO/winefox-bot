package com.github.winefoxbot.core.aitools;

import com.github.winefoxbot.plugins.chat.ChatPlugin;
import com.mikuac.shiro.dto.event.message.PrivateMessageEvent;
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
    @Description("""
    Get the current detailed date and time (Year, Month, Day, Weekday, Time).
    
    TRIGGER RULES:
    1. Call this tool ONLY when the user explicitly asks about time, date, or 'what day is it today'.
    2. Do NOT call this automatically for every message.
    """)
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
