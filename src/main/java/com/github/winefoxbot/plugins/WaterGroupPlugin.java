package com.github.winefoxbot.plugins;

import com.github.winefoxbot.annotation.PluginFunction;
import com.github.winefoxbot.model.entity.WaterGroupMessageStat;
import com.github.winefoxbot.model.entity.WaterGroupSchedule;
import com.github.winefoxbot.service.watergroup.WaterGroupPosterDrawService;
import com.github.winefoxbot.service.watergroup.WaterGroupScheduleService;
import com.github.winefoxbot.service.watergroup.WaterGroupService;
import com.github.winefoxbot.utils.FileUtil;
import com.mikuac.shiro.annotation.GroupMessageHandler;
import com.mikuac.shiro.annotation.MessageHandlerFilter;
import com.mikuac.shiro.annotation.common.Shiro;
import com.mikuac.shiro.common.utils.MsgUtils;
import com.mikuac.shiro.core.Bot;
import com.mikuac.shiro.dto.event.message.GroupMessageEvent;
import com.mikuac.shiro.enums.MsgTypeEnum;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.regex.Matcher;

/**
 * @author FlanChan (badapple495@outlook.com)
 * @since 2025-12-15-23:56
 */
@Shiro
@Component
@Slf4j
@RequiredArgsConstructor
public class WaterGroupPlugin {
    private final WaterGroupService waterGroupService;
    private final WaterGroupPosterDrawService waterGroupPosterDrawService;
    private final WaterGroupScheduleService waterGroupScheduleService;


    @Async
    @GroupMessageHandler
    public void countWaterGroupStats(GroupMessageEvent event) {
        Long groupId = event.getGroupId();
        Long userId = event.getSender().getUserId();
        waterGroupService.incrementMessageCount(groupId, userId);
    }

    @PluginFunction(group = "发言统计",
            name = "开启发言统计推送",
            description = "使用 /开启群发言统计每天定时推送 命令开启本群的发言统计功推送能。",
            permission = "普通用户",
            commands = {"/开启群发言统计每天定时推送", "/打开群发言统计每天定时推送", "/启动群发言统计每天定时推送"})
    @GroupMessageHandler
    @MessageHandlerFilter(types = MsgTypeEnum.text, cmd = "^/(开启|打开|启动)群发言统计每天定时推送$")
    public void enableWaterGroupStatsPush(Bot bot, GroupMessageEvent event) {
        Long groupId = event.getGroupId();
        if (waterGroupScheduleService.checkScheduled(groupId)) {
            bot.sendGroupMsg(groupId, "群发言统计每天定时推送功能已开启，无需重复开启！", false);
            return;
        }
        LocalTime sendTime = LocalTime.of(22, 0);
        waterGroupScheduleService.scheduleDailyPush(groupId, sendTime);
        // 向用户发送确认消息
        bot.sendGroupMsg(groupId, "已开启群发言统计每天定时推送！将在每天的%s".formatted(sendTime.toString()), false);
    }

    @PluginFunction(group = "发言统计",
            name = "修改发言统计推送时间",
            description = "使用 /修改发言统计推送时间 HH:mm 命令修改本群的发言统计推送时间。",
            permission = "普通用户",
            commands = {"/修改发言统计推送时间"})
    @GroupMessageHandler
    @MessageHandlerFilter(types = MsgTypeEnum.text, cmd = "^/修改发言统计推送时间\\s+(\\d{2}:\\d{2})$")
    public void modifyWaterGroupStatsPushTime(Bot bot, GroupMessageEvent event, Matcher matcher) {
        Long groupId = event.getGroupId();
        if (!waterGroupScheduleService.checkScheduled(groupId)) {
            bot.sendGroupMsg(groupId, "群发言统计每天定时推送功能未开启，无法修改推送时间！请先使用 /开启群发言-统计每天定时推送 命令开启该功能。", false);
            return;
        }

        // 使用 group(1) 直接获取时间字符串 "22:00"，干净利落
        String timeString = matcher.group(1);

        LocalTime newTime;
        try {
            newTime = LocalTime.parse(timeString);
        } catch (DateTimeParseException e) {
            // 这个捕获现在主要用于防御 "25:70" 这种无效但格式正确的时间
            bot.sendGroupMsg(groupId, "时间值无效，请使用有效的 HH:mm 格式，例如 22:00。", false);
            return;
        }

        waterGroupScheduleService.editDailyPush(groupId, newTime);
        bot.sendGroupMsg(groupId, "已修改发言统计推送时间为每天的 %s".formatted(newTime.toString()), false);
    }

    @PluginFunction(group = "发言统计",
            name = "关闭发言统计推送",
            description = "使用 /关闭群发言统计每天定时推送 命令关闭本群的发言统计推送功能。",
            permission = "普通用户",
            commands = {"/关闭群发言统计每天定时推送", "/停止群发言统计每天定时推送", "/取消群发言统计每天定时推送"})
    @GroupMessageHandler
    @MessageHandlerFilter(types = MsgTypeEnum.text, cmd = "^/(关闭|停止|取消)群发言统计每天定时推送$")
    public void disableWaterGroupStatsPush(Bot bot, GroupMessageEvent event) {
        Long groupId = event.getGroupId();
        if (!waterGroupScheduleService.checkScheduled(groupId)) {
            bot.sendGroupMsg(groupId, "群发言统计每天定时推送功能未开启，无需重复关闭！", false);
            return;
        }
        // 调用配置服务，设置开关为 false
        waterGroupScheduleService.unscheduleDailyPush(groupId);
        // 向用户发送确认消息
        bot.sendGroupMsg(groupId, "已关闭发言统计定时推送！", false);
    }


    @PluginFunction(group = "发言统计",
            name = "查看发言统计推送状态",
            description = "使用 /发言统计推送状态 命令查看本群的发言统计推送状态。",
            permission = "普通用户",
            commands = {"/每日发言统计推送状态"})
    @GroupMessageHandler
    @MessageHandlerFilter(types = MsgTypeEnum.text, cmd = "^/每日发言统计推送状态$")
    public void checkWaterGroupStatsPushStatus(Bot bot, GroupMessageEvent event) {
        Long groupId = event.getGroupId();
        WaterGroupSchedule waterGroupSchedule = waterGroupScheduleService.getScheduleJob(groupId);
        if (waterGroupSchedule == null) {
            bot.sendGroupMsg(groupId, "本群的发言统计推送状态：未开启", false);
            return;
        }
        bot.sendGroupMsg(groupId, "本群的发言统计推送状态：已开启，推送时间为每天的 %s".formatted(waterGroupSchedule.getTime().toString()), false);
    }


    @PluginFunction(group = "发言统计",
            name = "查看发言统计",
            description = "使用 /今日发言 命令查看本群的发言统计排名。",
            permission = "普通用户",
            commands = {"/今日发言"})
    @GroupMessageHandler
    @MessageHandlerFilter(types = MsgTypeEnum.text, cmd = "^/今日发言$")
    public void showWaterGroupStats(Bot bot, GroupMessageEvent event) {
        Long groupId = event.getGroupId();
        List<WaterGroupMessageStat> ranks = waterGroupService.getDailyRanking(groupId);
        if (ranks.isEmpty()) {
            bot.sendGroupMsg(groupId, "没有足够的数据生成统计", false);
            return;
        }
        bot.sendGroupMsg(groupId, "正在生成今日发言统计图片", false);

        File image = null;
        try {
            image = waterGroupPosterDrawService.drawPoster(ranks);
            bot.sendGroupMsg(groupId, MsgUtils.builder()
                    .img(FileUtil.getFileUrlPrefix() + image.getAbsolutePath())
                    .build(), false);
        } catch (IOException e) {
            log.error("生成发言统计图片失败", e);
            bot.sendGroupMsg(groupId, "生成发言统计图片失败，请稍后再试。", false);
        } finally {
            if (image != null && image.exists()) {
                image.delete();
            }
        }
    }

}