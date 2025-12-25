package com.github.winefoxbot.plugins;

import com.github.winefoxbot.annotation.PluginFunction;
import com.mikuac.shiro.annotation.AnyMessageHandler;
import com.mikuac.shiro.annotation.MessageHandlerFilter;
import com.mikuac.shiro.annotation.common.Shiro;
import com.mikuac.shiro.common.utils.MsgUtils;
import com.mikuac.shiro.core.Bot;
import com.mikuac.shiro.dto.event.message.AnyMessageEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.concurrent.*;
import java.util.regex.Matcher;

/**
 * @author FlanChan (badapple495@outlook.com)
 * @since 2025-12-12-15:15
 */
@Shiro
@Component
@RequiredArgsConstructor
public class ScheduleTipTaskPlugin {

    private final ScheduledExecutorService scheduler;

    private final Map<Long,ScheduledFuture<?>> scheduledTasks = new ConcurrentHashMap<>(1000);

    @PluginFunction(group = "实用功能", name = "定时提醒", description = "使用 /定时 提醒时间 提醒内容 命令设置定时提醒，使用 /取消定时提醒 取消提醒，使用 /定时提醒状态 查看当前提醒状态。时间格式支持数字加单位（s=秒，m=分钟，h=小时）或日期时间格式（如：15:30:00）。", commands = {"/定时 提醒时间 提醒内容", "/取消定时提醒", "/定时提醒状态"})
    @AnyMessageHandler
    @MessageHandlerFilter(cmd = "^/定时提醒\\s+(\\S+|\\d{1,2}:\\d{2})\\s+(.+)$")
    public void handleScheduleTask(Bot bot, AnyMessageEvent event, Matcher matcher) {
        try {
            Long userId = event.getUserId();
            //格式检查
            if (!matcher.matches() || matcher.groupCount() < 2) {
                bot.sendMsg(event, MsgUtils.builder()
                        .at(event.getUserId())
                        .text(" 输入格式错误，请使用：/定时 时间 消息")
                        .build(), false);
                return;
            }
            String time = matcher.group(1).trim();
            String tipMessage = matcher.group(2).trim();

            // 检查用户是否已有定时任务
            ScheduledFuture<?> scheduledTask = scheduledTasks.get(userId);
            if (scheduledTask != null) {
                bot.sendMsg(event, MsgUtils.builder()
                        .at(userId)
                        .text(" 您已有一个正在进行的定时任务，请先取消后再设置新的任务。")
                        .build(), false);
                return;
            }

            // 解析时间（支持分钟、秒、小时）


            long delay;
            TimeUnit timeUnit;
            try {
                if (time.endsWith("s")) {
                    delay = Long.parseLong(time.replace("s", "").trim());
                    delay = delay * 1000; // 转换为毫秒
                    timeUnit = TimeUnit.MILLISECONDS;
                } else if (time.endsWith("m")) {
                    delay = Long.parseLong(time.replace("m", "").trim());
                    delay = delay * 60 * 1000; // 转换为毫秒
                    timeUnit = TimeUnit.MILLISECONDS;
                } else if (time.endsWith("h")) {
                    delay = Long.parseLong(time.replace("h", "").trim());
                    delay = delay * 60 * 60 * 1000; // 转换为毫秒
                    timeUnit = TimeUnit.MILLISECONDS;
                } else {
                    // 尝试解析日期时间格式
                    try {
                        LocalTime targetTime = parseTime(time);
                        LocalDateTime now = LocalDateTime.now();
                        LocalDateTime targetDateTime = now.with(targetTime);
                        if (targetDateTime.isBefore(now)) {
                            targetDateTime = targetDateTime.plusDays(1); // Schedule for the next day if the time has passed
                        }
                        delay = Duration.between(now, targetDateTime).toMillis();
                        timeUnit = TimeUnit.MILLISECONDS;
                    } catch (Exception e) {
                        bot.sendMsg(event, MsgUtils.builder()
                                .at(userId)
                                .text(" 时间格式错误，请使用数字加单位（s=秒，m=分钟，h=小时）或日期时间格式（如：15:30:00）。")
                                .build(), false);
                        return;
                    }
                }
            } catch (NumberFormatException ex) {
                bot.sendMsg(event, MsgUtils.builder()
                        .at(userId)
                        .text(" 时间格式错误，请输入正确的数字和单位（如：30s, 1m, 2h）")
                        .build(), false);
                return;
            }
            // 调度任务
            ScheduledFuture<?> scheduleTask = scheduler.schedule(() -> {
                bot.sendMsg(event, MsgUtils.builder()
                        .at(userId)
                        .text(" 提醒：" + tipMessage)
                        .build(), false);
                // 任务完成后移除记录
                scheduledTasks.remove(userId);
            }, delay, timeUnit);
            // 记录已设置的任务
            scheduledTasks.put(userId,scheduleTask);
            // 定时任务信息格式化输出
            String message;
            // 当时间单位为毫秒时，转化为小时、分钟、秒
            long totalSeconds = delay / 1000;
            long hours = totalSeconds / 3600;
            long minutes = (totalSeconds % 3600) / 60;
            long seconds = totalSeconds % 60;

            // 根据是否有小时、分钟、秒来构造字符串
            StringBuilder timeString = new StringBuilder();
            if (hours > 0) {
                timeString.append(hours).append("小时");
            }
            if (minutes > 0 || hours > 0) { // 如果有小时或者有分钟
                timeString.append(minutes).append("分钟");
            }
            timeString.append(seconds).append("秒");
            message = String.format("定时任务已设置，将在 %s 后提醒您。", timeString.toString());
            // 发送消息
            bot.sendMsg(event, message, false);
        } catch (Exception ex) {
            bot.sendMsg(event, MsgUtils.builder()
                            .at(event.getUserId())
                            .text(" 设置定时任务失败，请重试。")
                    .build(), false);
            ex.printStackTrace();
        }
    }

    // 解析时间字符串
    private LocalTime parseTime(String time) {
        // 定义时间格式化器
        DateTimeFormatter formatterWithSeconds = DateTimeFormatter.ofPattern("HH:mm:ss");

        try {
            // 尝试解析 HH:mm:ss 格式
            return LocalTime.parse(time, formatterWithSeconds);
        } catch (Exception e) {
            // 如果失败，尝试解析 HH:mm 格式，并将秒设为 00
            return LocalTime.parse(time + ":00", formatterWithSeconds);
        }
    }



    @AnyMessageHandler
    @MessageHandlerFilter(cmd = "^/取消定时提醒$")
    public void handleCancelScheduleTask(Bot bot, AnyMessageEvent event, Matcher matcher) {
        Long userId = event.getUserId();
        ScheduledFuture<?> scheduledTask = scheduledTasks.get(userId);
        if (scheduledTask != null) {
            scheduledTask.cancel(true);
            scheduledTasks.remove(userId);
            bot.sendMsg(event,
                    MsgUtils.builder()
                            .at(userId)
                            .text(" 您的定时任务已取消。")
                            .build(), false);
        } else {
            bot.sendMsg(event,
                    MsgUtils.builder()
                            .at(userId)
                            .text(" 您没有正在进行的定时任务。")
                            .build()
                    , false);
        }
    }


    @AnyMessageHandler
    @MessageHandlerFilter(cmd = "^/定时提醒状态$")
    public void handleViewScheduleTask(Bot bot, AnyMessageEvent event, Matcher matcher) {
        Long userId = event.getUserId();
        ScheduledFuture<?> scheduledTask = scheduledTasks.get(userId);
        if (scheduledTask != null) {
            long delay = scheduledTask.getDelay(TimeUnit.MILLISECONDS);
            // 当时间单位为毫秒时，转化为小时、分钟、秒
            long totalSeconds = delay / 1000;
            long hours = totalSeconds / 3600;
            long minutes = (totalSeconds % 3600) / 60;
            long seconds = totalSeconds % 60;

            // 根据是否有小时、分钟、秒来构造字符串
            StringBuilder timeString = new StringBuilder();
            if (hours > 0) {
                timeString.append(hours).append("小时");
            }
            if (minutes > 0 || hours > 0) { // 如果有小时或者有分钟
                timeString.append(minutes).append("分钟");
            }
            timeString.append(seconds).append("秒");
            String message = String.format(" 定时任务已设置，将在 %s 后提醒您。", timeString.toString());
            bot.sendMsg(event,
                    MsgUtils.builder()
                            .at(userId)
                            .text(message)
                            .build(), false);
        } else {
            bot.sendMsg(event,
                    MsgUtils.builder()
                            .at(userId)
                            .text(" 您没有正在进行的定时任务。")
                            .build()
                    , false);
        }
    }

}