package com.github.winefoxbot.plugins.dailyreport;

import com.github.winefoxbot.core.annotation.plugin.Plugin;
import com.github.winefoxbot.core.annotation.plugin.PluginFunction;
import com.github.winefoxbot.core.model.entity.ShiroScheduleTask;
import com.github.winefoxbot.core.model.enums.common.Permission;
import com.github.winefoxbot.core.model.enums.common.PushTargetType;
import com.github.winefoxbot.core.service.schedule.ShiroScheduleTaskService;
import com.github.winefoxbot.core.util.CronFormatter;
import com.github.winefoxbot.plugins.dailyreport.job.DailyReportJob;
import com.github.winefoxbot.plugins.dailyreport.service.DailyReportService;
import com.mikuac.shiro.annotation.AnyMessageHandler;
import com.mikuac.shiro.annotation.GroupMessageHandler;
import com.mikuac.shiro.annotation.MessageHandlerFilter;
import com.mikuac.shiro.common.utils.MsgUtils;
import com.mikuac.shiro.core.Bot;
import com.mikuac.shiro.dto.event.message.AnyMessageEvent;
import com.mikuac.shiro.dto.event.message.GroupMessageEvent;
import com.mikuac.shiro.enums.MsgTypeEnum;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.util.regex.Matcher;

/**
 * @author FlanChan
 */
@Plugin(name = "酒狐日报", order = 10, permission = Permission.USER, description = "提供酒狐日报的订阅和获取功能。")
@Slf4j
@RequiredArgsConstructor
public class DailyReportPlugin {
    private final ShiroScheduleTaskService scheduleService;
    private final DailyReportService dailyReportService;

    /**
     * 开启或更新本群的酒狐日报自动推送
     */
    @PluginFunction(permission = Permission.ADMIN, name = "酒狐日报", description = "订阅酒狐日报，使用命令 /订阅酒狐日报 [时间 HH:mm]", commands = "/订阅酒狐日报 [HH:mm]")
    @GroupMessageHandler
    @MessageHandlerFilter(types = MsgTypeEnum.text, cmd = "^/订阅酒狐日报(?:\\s+([0-2][0-9]):([0-5][0-9]))?$")
    public void enableDailyReport(Bot bot, GroupMessageEvent event, Matcher matcher) {
        long groupId = event.getGroupId();
        String hourStr = matcher.group(1);
        String minuteStr = matcher.group(2);

        String cronExpression;
        String descTime;

        if (hourStr != null && minuteStr != null) {
            int hour = Integer.parseInt(hourStr);
            int minute = Integer.parseInt(minuteStr);
            cronExpression = "0 %d %d * * *".formatted(minute, hour);
            descTime = "%02d:%02d".formatted(hour, minute);
        } else {
            bot.sendGroupMsg(groupId, "❌ 请提供有效的时间参数，格式为 /订阅酒狐日报 [HH:mm]，例如 /订阅酒狐日报 08:30 。", false);
            return;
        }

        scheduleService.scheduleHandler(
                bot.getSelfId(),
                PushTargetType.GROUP,
                groupId,
                cronExpression,
                DailyReportJob.class
        );

        bot.sendGroupMsg(groupId, "✅ 配置更新成功！本群的酒狐日报将在 " + descTime + " 发送。", false);
    }




    /**
     * 查看订阅状态
     */
    @PluginFunction(name = "查看酒狐日报订阅", description = "查看当前群的日报订阅状态", permission = Permission.USER, commands = "/查看酒狐日报订阅")
    @GroupMessageHandler
    @MessageHandlerFilter(types = MsgTypeEnum.text, cmd = "^/查看酒狐日报订阅$")
    public void checkDailyReportStatus(Bot bot, GroupMessageEvent event) {
        long groupId = event.getGroupId();

        ShiroScheduleTask schedule = scheduleService.getTaskConfig(bot.getSelfId(),PushTargetType.GROUP, groupId, DailyReportJob.class);

        if (schedule != null && schedule.getIsEnabled()) {
            String readableTime = CronFormatter.parseCronToDescription(schedule.getCronExpression());
            String msg = """
                    ✅ 当前群已订阅酒狐日报
                    ⏰ 推送时间: %s
                    🤖 执行Bot: %s
                    """.formatted(readableTime, schedule.getBotId());
            bot.sendGroupMsg(groupId, msg, false);
        } else {
            bot.sendGroupMsg(groupId, "❌ 当前群尚未订阅酒狐日报。", false);
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
        // 新版 API 删除任务
        scheduleService.cancelTask(bot.getSelfId(),PushTargetType.GROUP, groupId, DailyReportJob.class);
        bot.sendGroupMsg(groupId, "本群的酒狐日报推送已关闭。", false);
    }


    @PluginFunction(permission = Permission.ADMIN, name = "强制刷新酒狐日报", description = "强制刷新酒狐日报", commands = "/刷新酒狐日报")
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
