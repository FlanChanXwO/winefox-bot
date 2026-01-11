package com.github.winefoxbot.plugins.dailyreport.model.dto;

import org.jspecify.annotations.NonNull;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * Holiday 数据传输对象 (DTO), 使用 record 类型定义.
 * record 自动提供了构造函数、getter、equals、hashCode 和 toString 方法.
 */
public record HolidayDTO(int daysLeft, String name, LocalDate date) implements Serializable  {

    // 你可以覆盖默认的 toString 方法来自定义输出格式
    @Override
    public @NonNull String toString() {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        return String.format("距离 %s (%s) 还有 %d 天", name, date.format(formatter), daysLeft);
    }
}
