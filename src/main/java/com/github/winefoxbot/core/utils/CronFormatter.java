package com.github.winefoxbot.core.utils;

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

/**
 * Cron 表达式格式化工具类
 * 用于将 Cron 表达式转换为人类可读的中文描述
 * @author FlanChan
 */
@Slf4j
@UtilityClass
public class CronFormatter {

    /**
     * 将 Cron 表达式解析为用户友好的字符串
     *
     * @param cronExpression Cron 表达式 (支持 5 位或 6 位格式)
     * @return 中文描述
     */
    public String parseCronToDescription(String cronExpression) {
        if (cronExpression == null || cronExpression.isBlank()) {
            return "未设置时间";
        }

        try {
            String[] fields = cronExpression.trim().split("\\s+");
            
            // 兼容性处理：Spring Task / Quartz 通常是 6 位 (秒 分 时 日 月 周)
            // Linux Crontab 通常是 5 位 (分 时 日 月 周)
            String minute;
            String hour;
            String dayOfMonth;
            String dayOfWeek;

            if (fields.length == 6) {
                // 忽略秒 (index 0)
                minute = fields[1];
                hour = fields[2];
                dayOfMonth = fields[3];
                // index 4 is Month
                dayOfWeek = fields[5];
            } else if (fields.length == 5) {
                minute = fields[0];
                hour = fields[1];
                dayOfMonth = fields[2];
                // index 3 is Month
                dayOfWeek = fields[4];
            } else {
                return "未知时间格式";
            }

            // 格式化时间部分
            String timePart = "%02d:%02d".formatted(Integer.parseInt(hour), Integer.parseInt(minute));

            // 判断类型
            boolean isDaily = "*".equals(dayOfMonth) && ("*".equals(dayOfWeek) || "?".equals(dayOfWeek));
            boolean isWeekly = "*".equals(dayOfMonth) && !"*".equals(dayOfWeek) && !"?".equals(dayOfWeek);
            boolean isMonthly = !"*".equals(dayOfMonth) && ("*".equals(dayOfWeek) || "?".equals(dayOfWeek));

            if (isDaily) {
                return "每日 " + timePart;
            }

            if (isWeekly) {
                return "每周%s %s".formatted(convertDayOfWeek(dayOfWeek), timePart);
            }

            if (isMonthly) {
                if ("L".equalsIgnoreCase(dayOfMonth)) {
                    return "每月最后一天 " + timePart;
                }
                return "每月%s日 %s".formatted(dayOfMonth, timePart);
            }

            return "自定义时间 (" + cronExpression + ")";

        } catch (Exception e) {
            log.warn("解析Cron表达式失败: {}", cronExpression);
            return "时间格式异常";
        }
    }

    /**
     * 转换星期字段
     */
    private String convertDayOfWeek(String dayOfWeek) {
        return switch (dayOfWeek.toUpperCase()) {
            case "1", "SUN" -> "日";
            case "2", "MON" -> "一";
            case "3", "TUE" -> "二";
            case "4", "WED" -> "三";
            case "5", "THU" -> "四";
            case "6", "FRI" -> "五";
            case "7", "SAT" -> "六";
            default -> dayOfWeek;
        };
    }
}
