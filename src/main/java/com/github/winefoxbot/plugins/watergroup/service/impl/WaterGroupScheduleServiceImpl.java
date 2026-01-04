package com.github.winefoxbot.plugins.watergroup.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.github.winefoxbot.plugins.watergroup.mapper.WaterGroupScheduleMapper;
import com.github.winefoxbot.plugins.watergroup.model.entity.WaterGroupMessageStat;
import com.github.winefoxbot.plugins.watergroup.model.entity.WaterGroupSchedule;
import com.github.winefoxbot.core.service.schedule.ScheduleTaskService;
import com.github.winefoxbot.plugins.watergroup.service.WaterGroupScheduleService;
import com.github.winefoxbot.plugins.watergroup.service.WaterGroupService;
import com.github.winefoxbot.core.utils.FileUtil;
import com.mikuac.shiro.common.utils.MsgUtils;
import com.mikuac.shiro.core.Bot;
import com.mikuac.shiro.core.BotContainer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jobrunr.scheduling.cron.Cron;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.io.IOException;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

/**
 * @author FlanChan
 * @description 针对表【water_group_schedule】的数据库操作Service实现
 * @createDate 2025-12-24 11:53:16
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WaterGroupScheduleServiceImpl extends ServiceImpl<WaterGroupScheduleMapper, WaterGroupSchedule>
        implements WaterGroupScheduleService {
    private final BotContainer botContainer;
    private final WaterGroupService waterGroupService;
    private final WaterGroupPosterDrawServiceImpl waterGroupPosterDrawService;
    private final ScheduleTaskService scheduleTaskService;
    private final String JOB_ID = "water-group-push-%s";

    @Override
    @Transactional
    public void scheduleDailyPush(Long groupId, LocalTime time) {
        scheduleTaskService.scheduleOrUpdateRecurrentTask(
                JOB_ID.formatted(groupId),
                Cron.daily(time.getHour(),time.getMinute()),
                () -> executeDailyPush(groupId)
        );
        WaterGroupSchedule waterGroupSchedule = new WaterGroupSchedule();
        waterGroupSchedule.setGroupId(groupId);
        waterGroupSchedule.setTime(time);
        boolean saved = this.save(waterGroupSchedule);
        if (!saved) {
            log.error("保存 WaterGroupSchedule 失败，groupId: {}", groupId);
        }
    }


    @Override
    @Transactional
    public void editDailyPush(Long groupId, LocalTime time) {
        scheduleTaskService.scheduleOrUpdateRecurrentTask(
                JOB_ID.formatted(groupId),
                Cron.daily(time.getHour(),time.getMinute()),
                () -> executeDailyPush(groupId)
        );
        WaterGroupSchedule waterGroupSchedule = new WaterGroupSchedule();
        waterGroupSchedule.setGroupId(groupId);
        waterGroupSchedule.setTime(time);
        LambdaQueryWrapper<WaterGroupSchedule> updateWrapper = new LambdaQueryWrapper<>();
        updateWrapper.eq(WaterGroupSchedule::getGroupId, groupId);
        boolean success = this.update(waterGroupSchedule, updateWrapper);

        if (!success) {
            log.error("更新 WaterGroupSchedule 失败，groupId: {}", groupId);
        }
    }

    @Override
    public WaterGroupSchedule getScheduleJob(Long groupId) {
        return this.getOne(new LambdaQueryWrapper<>(WaterGroupSchedule.class).eq(WaterGroupSchedule::getGroupId, groupId));
    }


    @Override
    public boolean checkScheduled(Long groupId) {
        return this.getOne(new LambdaQueryWrapper<>(WaterGroupSchedule.class).eq(WaterGroupSchedule::getGroupId, groupId)) != null;
    }

    @Transactional
    @Override
    public void unscheduleDailyPush(Long groupId) {
        scheduleTaskService.deleteRecurrentTask(JOB_ID.formatted(groupId));
        boolean removed = this.remove(new LambdaQueryWrapper<>(WaterGroupSchedule.class).eq(WaterGroupSchedule::getGroupId, groupId));
        if (!removed) {
            log.error("删除 WaterGroupSchedule 失败，groupId: {}", groupId);
        }
    }

    public void executeDailyPush(Long groupId) {
        // 获取第一个bot
        Optional<Bot> botOptional = botContainer.robots.values().stream().findFirst();
        if (botOptional.isEmpty()) {
            return;
        }
        Bot bot = botOptional.get();
        log.info("今日发言统计图片推送任务执行，群ID: {}", groupId);
        List<WaterGroupMessageStat> ranks = waterGroupService.getDailyRanking(groupId);
        if (ranks.isEmpty()) {
            bot.sendGroupMsg(groupId, "没有足够的数据生成统计", false);
            return;
        }

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
        } finally {
            if (image != null && image.exists()) {
                image.delete();
            }
        }
    }


}




