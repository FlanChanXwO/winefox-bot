package com.github.winefoxbot.plugins.watergroup.service.impl;

import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.github.winefoxbot.core.model.entity.GroupPushSchedule;
import com.github.winefoxbot.core.service.push.GroupPushTaskExecutor;
import com.github.winefoxbot.core.service.schedule.GroupPushScheduleService;
import com.github.winefoxbot.core.utils.FileUtil;
import com.github.winefoxbot.plugins.watergroup.WaterGroupPlugin;
import com.github.winefoxbot.plugins.watergroup.model.entity.WaterGroupMessageStat;
import com.github.winefoxbot.plugins.watergroup.model.entity.WaterGroupSchedule;
import com.github.winefoxbot.plugins.watergroup.service.WaterGroupScheduleService;
import com.github.winefoxbot.plugins.watergroup.service.WaterGroupService;
import com.mikuac.shiro.common.utils.MsgUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jobrunr.scheduling.cron.Cron;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.time.LocalTime;
import java.util.List;

/**
 * @author FlanChan
 * 针对 unified `group_push_schedule` 的 WaterGroup 适配实现
 */
@Service
@RequiredArgsConstructor
@Slf4j
        public class WaterGroupScheduleServiceImpl implements WaterGroupScheduleService {

    private final WaterGroupService waterGroupService;
    private final WaterGroupPosterDrawServiceImpl waterGroupPosterDrawService;
    private final GroupPushScheduleService groupPushScheduleService;
    private final GroupPushTaskExecutor groupPushTaskExecutor;

    private static final String TASK_TYPE = "WATER_GROUP_STAT";

    @Override
    public void scheduleDailyPush(Long groupId, LocalTime time) {
        String cron = Cron.daily(time.getHour(), time.getMinute());
        // JobRunr 需要 lambda 指向的方法是 bean 方法。
        groupPushScheduleService.scheduleTask(
                groupId,
                TASK_TYPE,
                null,
                cron,
                "每日发言统计推送",
                () -> executeDailyPush(groupId)
        );
    }

    @Override
    public void editDailyPush(Long groupId, LocalTime time) {
        // scheduleTask handles both create and update
        scheduleDailyPush(groupId, time);
    }

    @Override
    public WaterGroupSchedule getScheduleJob(Long groupId) {
        GroupPushSchedule config = groupPushScheduleService.getTaskConfig(groupId, TASK_TYPE, null);
        if (config == null) {
            return null;
        }
        return convertToLegacyDto(config);
    }

    @Override
    public boolean checkScheduled(Long groupId) {
        return groupPushScheduleService.getTaskConfig(groupId, TASK_TYPE, null) != null;
    }

    @Override
    public void unscheduleDailyPush(Long groupId) {
        groupPushScheduleService.unscheduleTask(groupId, TASK_TYPE, null);
    }

    /**
     * JobRunr 调用的目标方法，必须保持 public
     */
    public void executeDailyPush(Long groupId) {
        groupPushTaskExecutor.execute(groupId, "今日发言统计推送", (bot) -> {
            List<WaterGroupMessageStat> ranks = waterGroupService.getDailyRanking(groupId);
            if (ranks.isEmpty()) {
                bot.sendGroupMsg(groupId, "没有足够的数据生成统计", false);
                return;
            }
            ScopedValue.where(WaterGroupPlugin.CURRENT_GROUP_ID, groupId).run(() -> {
                File image = null;
                try {
                    image = waterGroupPosterDrawService.drawPoster(ranks);
                    bot.sendGroupMsg(groupId, "那么，这是今天的活跃榜~", false);
                    bot.sendGroupMsg(groupId, MsgUtils.builder()
                            .img(FileUtil.getFileUrlPrefix() + image.getAbsolutePath())
                            .build(), false);

                } catch (IOException e) {
                    log.error("生成发言统计图片失败", e);
                    bot.sendGroupMsg(groupId, "生成发言统计图片失败，请稍后再试。", false);
                    throw new RuntimeException("生成图片失败", e);
                } finally {
                    if (image != null && image.exists()) {
                        image.delete();
                    }
                }
            });
        });
    }

    private WaterGroupSchedule convertToLegacyDto(GroupPushSchedule config) {
        WaterGroupSchedule dto = new WaterGroupSchedule();
        dto.setGroupId(config.getGroupId());
        // Simple parser for standard 5 or 6 fields cron: "m h * * *"
        // If complex, this return null or default
        dto.setTime(parseTimeFromCron(config.getCronExpression()));
        return dto;
    }

    private LocalTime parseTimeFromCron(String cron) {
        try {
            if (StringUtils.isBlank(cron)) return null;
            String[] parts = cron.trim().split("\\s+");
            // Assuming "minute hour ..."
            int minute = Integer.parseInt(parts[0]);
            int hour = Integer.parseInt(parts[1]);
            return LocalTime.of(hour, minute);
        } catch (Exception e) {
            log.error("无法解析Cron表达式为时间: {}", cron);
            return LocalTime.of(0, 0);
        }
    }
}

