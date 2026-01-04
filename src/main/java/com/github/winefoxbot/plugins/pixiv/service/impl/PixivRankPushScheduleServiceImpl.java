package com.github.winefoxbot.plugins.pixiv.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.github.winefoxbot.plugins.pixiv.mapper.PixivRankPushScheduleMapper;
import com.github.winefoxbot.plugins.pixiv.model.entity.PixivRankPushSchedule;
import com.github.winefoxbot.plugins.pixiv.model.enums.PixivRankPushMode;
import com.github.winefoxbot.plugins.pixiv.service.PixivRankPushScheduleService;
import com.github.winefoxbot.plugins.pixiv.service.PixivRankService;
import com.github.winefoxbot.core.service.schedule.ScheduleTaskService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;

/**
* @author FlanChan
* @description 针对表【pixiv_rank_push_schedule】的数据库操作Service实现
* @createDate 2025-12-28 19:46:20
*/
@Service
@RequiredArgsConstructor
@Slf4j
public class PixivRankPushScheduleServiceImpl extends ServiceImpl<PixivRankPushScheduleMapper, PixivRankPushSchedule>
    implements PixivRankPushScheduleService{

    private final ScheduleTaskService scheduleTaskService;
    private final PixivRankService pixivRankService;

    private static final String JOB_ID_TEMPLATE = "pixiv-rank-push-%s-%s"; // {rankType}-{groupId}

    @Override
    @Transactional
    public void schedulePush(Long groupId, PixivRankPushMode mode, String cronExpression, String description) {
        String jobId = String.format(JOB_ID_TEMPLATE, mode.getValue(), groupId);

        // 使用新的调度服务来创建或更新定时任务
        scheduleTaskService.scheduleOrUpdateRecurrentTask(
                jobId,
                cronExpression,
                () -> executePush(groupId, mode.getValue()) // 定义任务执行的具体逻辑
        );

        // 从数据库查找是否已存在该订阅
        PixivRankPushSchedule schedule = getSchedule(groupId, mode);
        if (schedule == null) {
            schedule = new PixivRankPushSchedule();
            schedule.setGroupId(groupId);
            schedule.setRankType(mode.getValue());
        }
        // 更新或插入数据
        schedule.setCronSchedule(cronExpression);
        schedule.setDescription(description);
        this.saveOrUpdate(schedule);
        log.info("成功调度P站排行榜推送任务: [{}], Cron: [{}], GroupId: [{}]", mode.getValue(), cronExpression, groupId);
    }

    @Override
    @Transactional
    public boolean unschedulePush(Long groupId, PixivRankPushMode mode) {
        String jobId = String.format(JOB_ID_TEMPLATE, mode.getValue(), groupId);
        // 删除定时任务
        scheduleTaskService.deleteRecurrentTask(jobId);

        // 删除数据库记录
        boolean removed = this.remove(new LambdaQueryWrapper<PixivRankPushSchedule>()
                .eq(PixivRankPushSchedule::getGroupId, groupId)
                .eq(PixivRankPushSchedule::getRankType, mode.getValue()));

        if (removed) {
            log.info("成功取消P站排行榜推送任务: [{}], GroupId: [{}]", mode.getValue(), groupId);
        } else {
            log.warn("尝试取消一个不存在的P站排行榜推送任务: [{}], GroupId: [{}]", mode.getValue(), groupId);
        }
        return removed;
    }

    @Override
    public List<PixivRankPushSchedule> getSchedulesByGroupId(Long groupId) {
        return this.list(new LambdaQueryWrapper<PixivRankPushSchedule>()
                .eq(PixivRankPushSchedule::getGroupId, groupId));
    }

    @Override
    public PixivRankPushSchedule getSchedule(Long groupId, PixivRankPushMode mode) {
        return this.getOne(new LambdaQueryWrapper<PixivRankPushSchedule>()
                .eq(PixivRankPushSchedule::getGroupId, groupId)
                .eq(PixivRankPushSchedule::getRankType, mode.getValue()));
    }

    /**
     * 将Cron表达式解析为用户友好的字符串
     * @param cronExpression Cron表达式
     * @return 可读的描述
     */
    @Override
    public String parseCronToDescription(String cronExpression) {
        try {
            String[] fields = cronExpression.split(" ");
            log.info("解析Cron表达式: {}, 分解字段: {}", cronExpression, Arrays.toString(fields));
            String minute = fields[0];
            String hour = fields[1];
            String dayOfMonth = fields[2];
            String dayOfWeek = fields[4];

            String timePart = String.format("%s时%s分", hour, minute);

            // 每月
            if (!dayOfMonth.equals("*") && dayOfWeek.equals("*")) {
                if (dayOfMonth.equals("L")) {
                    return String.format("每月最后一天 %s", timePart);
                }
                return String.format("每月%s日 %s", dayOfMonth, timePart);
            }
            // 每周
            if (dayOfMonth.equals("*") && !dayOfWeek.equals("*")) {
                return String.format("每周%s %s", convertDayOfWeek(dayOfWeek), timePart);
            }
            // 每日
            if (dayOfMonth.equals("*") && dayOfWeek.equals("*")) {
                return String.format("每日 %s", timePart);
            }
            return "自定义时间"; // 其他复杂情况
        } catch (Exception e) {
            log.error("解析Cron表达式失败: {}", cronExpression, e);
            return "时间格式异常";
        }
    }

    private String convertDayOfWeek(String day) {
        return switch (day.toUpperCase()) {
            case "1", "SUN" -> "日";
            case "2", "MON" -> "一";
            case "3", "TUE" -> "二";
            case "4", "WED" -> "三";
            case "5", "THU" -> "四";
            case "6", "FRI" -> "五";
            case "7", "SAT" -> "六";
            default -> "未知";
        };
    }

    /**
     * 注意请不要使用枚举传参，因为这是定时任务执行的方法，枚举无法序列化
     * 定时任务实际执行的推送逻辑
     * @param groupId 群组ID
     * @param mode 排行榜类型 (daily, weekly, monthly)
     */
    public void executePush(Long groupId, String mode) {
        log.info("开始执行P站排行榜推送任务, 群组ID: {}, 类型: {}", groupId, mode);
        pixivRankService.fetchAndPushRank(groupId, PixivRankPushMode.fromValue(mode), PixivRankService.Content.ILLUST);
    }
}




