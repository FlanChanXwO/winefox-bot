package com.github.winefoxbot.plugins.dailyreport;

import com.github.winefoxbot.core.annotation.Plugin;
import com.github.winefoxbot.core.annotation.PluginFunction;
import com.github.winefoxbot.core.model.entity.GroupPushSchedule;

import com.github.winefoxbot.core.model.enums.Permission;
import com.github.winefoxbot.core.service.schedule.GroupPushScheduleService;
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

/**
 * @author FlanChan
 */
@Shiro
@Slf4j
@Plugin(name = "DailyReport", description = "日报")
@Component
@RequiredArgsConstructor
public class DailyReportPlugin{
    private final GroupPushScheduleService scheduleService;
    private final DailyReportService dailyReportService;

    private static final String DEFAULT_CRON = "0 1 9 * * ?";
    public static final String TASK_TYPE_DAILY_REPORT = "DAILY_REPORT";

    /**
     * 开启本群的真寻日报自动推送
     */
    @PluginFunction( permission = Permission.ADMIN, name = "日报", description = "开启真寻日报",commands = "/开启真寻日报")
    @GroupMessageHandler
    @MessageHandlerFilter(types = MsgTypeEnum.text, cmd = "^/开启真寻日报$")
    public void enableDailyReport(Bot bot, GroupMessageEvent event) {
        Long groupId = event.getGroupId();

        // 检查是否已存在任务
        GroupPushSchedule existingSchedule = scheduleService.getTaskConfig(groupId, TASK_TYPE_DAILY_REPORT,null);
        if (existingSchedule != null && existingSchedule.getIsEnabled()) {
            bot.sendGroupMsg(groupId, "本群已经开启了真寻日报推送哦~ 推送时间为：" + existingSchedule.getCronExpression(),false);
            return;
        }

        // 创建或更新任务
        scheduleService.scheduleTask(
                groupId,
                TASK_TYPE_DAILY_REPORT,
                null,
                DEFAULT_CRON,
                "真寻日报每日推送",
                () -> {}
        );
        bot.sendGroupMsg(groupId, "本群的真寻日报推送已开启！将会在每天早上9:01发送。", false);
    }

    /**
     * 关闭本群的真寻日报自动推送
     */
    @PluginFunction(name = "关闭真寻日报", description = "关闭真寻日报", permission = Permission.ADMIN, commands = "/关闭真寻日报")
    @GroupMessageHandler
    @MessageHandlerFilter(types = MsgTypeEnum.text, cmd = "^/关闭真寻日报$")
    public void disableDailyReport(Bot bot, GroupMessageEvent event) {
        Long groupId = event.getGroupId();

        GroupPushSchedule schedule = scheduleService.getTaskConfig(groupId, TASK_TYPE_DAILY_REPORT, null);
        if (schedule == null || !schedule.getIsEnabled()) {
            bot.sendGroupMsg(groupId, "本群尚未开启真寻日报推送哦~", false);
            return;
        }

        // 调用服务禁用任务，而不是删除
        scheduleService.unscheduleTask(groupId,TASK_TYPE_DAILY_REPORT,null);
        bot.sendGroupMsg(groupId, "本群的真寻日报推送已关闭。", false);
    }

    /**
     * 手动获取当天的日报
     */
    @PluginFunction( permission = Permission.USER, name = "获取日报", description = "手动获取当天的真寻日报",commands = {
            "/真寻日报", "真寻日报"
    })
    @AnyMessageHandler
    @MessageHandlerFilter(types = MsgTypeEnum.text, cmd = "^/?真寻日报$")
    public void getManualReport(Bot bot, AnyMessageEvent event) {
        try {
            byte[] imageBytes = dailyReportService.getDailyReportImage();
            String message = MsgUtils.builder().img(imageBytes).build();
            bot.sendMsg(event, message,false);
        } catch (Exception e) {
            log.error("手动生成日报失败", e);
            bot.sendMsg(event, "日报生成失败了，请联系管理员查看后台日志。",false);
        }
    }
}
