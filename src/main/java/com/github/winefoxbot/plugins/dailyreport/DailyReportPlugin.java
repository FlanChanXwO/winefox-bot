package com.github.winefoxbot.plugins.dailyreport;

import com.github.winefoxbot.core.annotation.Plugin;
import com.github.winefoxbot.core.annotation.PluginFunction;
import com.github.winefoxbot.core.model.entity.GroupPushSchedule;

import com.github.winefoxbot.core.model.enums.Permission;
import com.github.winefoxbot.core.service.schedule.GroupPushScheduleService;
import com.github.winefoxbot.core.utils.CronFormatter;
import com.github.winefoxbot.plugins.dailyreport.service.DailyReportService;
import com.mikuac.shiro.annotation.AnyMessageHandler;
import com.mikuac.shiro.annotation.GroupMessageHandler;
import com.mikuac.shiro.annotation.MessageHandlerFilter;
import com.mikuac.shiro.annotation.common.Shiro;
import com.mikuac.shiro.common.utils.MsgUtils;
import com.mikuac.shiro.core.Bot;
import com.mikuac.shiro.dto.event.message.AnyMessageEvent;
import com.mikuac.shiro.dto.event.message.GroupMessageEvent;
import com.mikuac.shiro.enums.MsgTypeEnum;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.regex.Matcher;

/**
 * @author FlanChan
 */
@Shiro
@Slf4j
@Plugin(name = "酒狐日报", description = "酒狐日报，获取每天的新番，IT资讯，科技动态等内容推送",order = 10)
@Component
@RequiredArgsConstructor
public class DailyReportPlugin {
    private final GroupPushScheduleService scheduleService;
    private final DailyReportService dailyReportService;

    public static final String TASK_TYPE_DAILY_REPORT = "DAILY_REPORT";

    /**
     * 开启或更新本群的酒狐日报自动推送
     */
    @PluginFunction(permission = Permission.ADMIN, name = "酒狐日报", description = "订阅酒狐日报，使用命令 /订阅酒狐日报 [时间 HH:mm]", commands = "/订阅酒狐日报 [HH:mm]")
    @GroupMessageHandler
    @MessageHandlerFilter(types = MsgTypeEnum.text, cmd = "^/订阅酒狐日报(?:\\s+([0-2][0-9]):([0-5][0-9]))?$")
    public void enableDailyReport(Bot bot, GroupMessageEvent event, Matcher matcher) {
        long groupId = event.getGroupId();

        // 这里的 matcher.group(1) 是小时, group(2) 是分钟
        String hourStr = matcher.group(1);
        String minuteStr = matcher.group(2);

        if (hourStr == null || minuteStr == null) {
            var help = """
                    指令格式错误或未指定时间！
                    用法: /订阅酒狐日报 HH:mm
                    示例:
                    /订阅酒狐日报 09:30
                    """;
            bot.sendGroupMsg(groupId, help, true);
            return;
        }

        int hour = Integer.parseInt(hourStr);
        int minute = Integer.parseInt(minuteStr);

        // 生成 Cron 表达式: 秒 分 时 日 月 周
        // 这里设置为每天指定时间执行
        String cronExpression = "0 %d %d * * ?".formatted(minute, hour);

        // 移除旧的检查逻辑，直接覆盖/更新任务
        scheduleService.scheduleTask(
                groupId,
                TASK_TYPE_DAILY_REPORT,
                null,
                cronExpression,
                "酒狐日报每日推送",
                () -> dailyReportService.executeDailyPush(groupId)
        );

        bot.sendGroupMsg(groupId, "配置更新成功！本群的酒狐日报将会在每天 %02d:%02d 发送。".formatted(hour, minute), false);
    }




    /**
     * 查看订阅状态 (新增)
     */
    @PluginFunction(name = "查看酒狐日报订阅", description = "查看当前群的日报订阅状态", permission = Permission.USER, commands = "/查看酒狐日报订阅")
    @GroupMessageHandler
    @MessageHandlerFilter(types = MsgTypeEnum.text, cmd = "^/查看酒狐日报订阅$")
    public void checkDailyReportStatus(Bot bot, GroupMessageEvent event) {
        long groupId = event.getGroupId();

        GroupPushSchedule schedule = scheduleService.getTaskConfig(groupId, TASK_TYPE_DAILY_REPORT, null);

        if (schedule != null && schedule.getIsEnabled()) {
            // 使用工具类解析 Cron
            String readableTime = CronFormatter.parseCronToDescription(schedule.getCronExpression());

            String msg = """
                    ✅ 当前群已订阅酒狐日报
                    ⏰ 推送时间: %s
                    """.formatted(readableTime);
            bot.sendGroupMsg(groupId, msg, false);
        } else {
            bot.sendGroupMsg(groupId, "❌ 当前群尚未订阅酒狐日报，或订阅已关闭。", false);
        }
    }

    @PluginFunction(permission = Permission.ADMIN, name = "强制刷新酒狐日报", description = "立即强制刷新并推送本群的酒狐日报", commands = "/刷新酒狐日报")
    @GroupMessageHandler
    @MessageHandlerFilter(types = MsgTypeEnum.text, cmd = "^/刷新酒狐日报$")
    public void forceRefreshDailyReport(Bot bot, GroupMessageEvent event) {
        long groupId = event.getGroupId();
        bot.sendGroupMsg(groupId, "正在强制刷新酒狐日报，请稍候...", false);
        try {
            dailyReportService.regenerateDailyReportImage();
            bot.sendGroupMsg(groupId, "已完成强制刷新。", false);
        } catch (Exception e) {
            log.error("强制刷新日报失败", e);
            bot.sendGroupMsg(groupId, "刷新失败，请联系管理员查看后台日志。", false);
        }
    }


    /**
     * 关闭本群的酒狐日报自动推送
     */
    @PluginFunction(name = "关闭酒狐日报", description = "关闭酒狐日报", permission = Permission.ADMIN, commands = "/关闭酒狐日报")
    @GroupMessageHandler
    @MessageHandlerFilter(types = MsgTypeEnum.text, cmd = "^/关闭酒狐日报$")
    public void disableDailyReport(Bot bot, GroupMessageEvent event) {
        long groupId = event.getGroupId();

        GroupPushSchedule schedule = scheduleService.getTaskConfig(groupId, TASK_TYPE_DAILY_REPORT, null);
        if (schedule == null || !schedule.getIsEnabled()) {
            bot.sendGroupMsg(groupId, "本群尚未开启酒狐日报推送哦~", false);
            return;
        }

        // 调用服务禁用任务
        scheduleService.unscheduleTask(groupId, TASK_TYPE_DAILY_REPORT, null);
        bot.sendGroupMsg(groupId, "本群的酒狐日报推送已关闭。", false);
    }

    /**
     * 手动获取当天的日报
     */
    @PluginFunction(permission = Permission.USER, name = "获取日报", description = "手动获取当天的酒狐日报", commands = {
            "/酒狐日报", "酒狐日报"
    })
    @AnyMessageHandler
    @MessageHandlerFilter(types = MsgTypeEnum.text, cmd = "^/?酒狐日报$")
    public void getManualReport(Bot bot, AnyMessageEvent event) {
        try {
            byte[] imageBytes = dailyReportService.getDailyReportImage();
            String message = MsgUtils.builder().img(imageBytes).build();
            bot.sendMsg(event, message, false);
        } catch (IOException e) {
            log.error("手动生成日报失败", e);
            bot.sendMsg(event, "日报生成失败了，请联系管理员查看后台日志。", false);
        }
    }
}
