package com.github.winefoxbot.plugins.pixiv.service.impl;

import com.github.winefoxbot.core.model.entity.GroupPushSchedule;
import com.github.winefoxbot.core.service.push.GroupPushTaskExecutor;
import com.github.winefoxbot.core.service.schedule.GroupPushScheduleService;
import com.github.winefoxbot.plugins.pixiv.model.entity.PixivRankPushSchedule;
import com.github.winefoxbot.plugins.pixiv.model.enums.PixivRankPushMode;
import com.github.winefoxbot.plugins.pixiv.service.PixivRankPushScheduleService;
import com.github.winefoxbot.plugins.pixiv.service.PixivRankService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author FlanChan
 * 针对 unified `group_push_schedule` 的 Pixiv 适配实现

 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PixivRankPushScheduleServiceImpl implements PixivRankPushScheduleService {

    private final PixivRankService pixivRankService;
    private final GroupPushTaskExecutor groupPushTaskExecutor;
    private final GroupPushScheduleService groupPushScheduleService;

    private static final String TASK_TYPE = "PIXIV_RANK_PUSH";

    @Override
    public void schedulePush(Long groupId, PixivRankPushMode mode, String cronExpression, String description) {
        groupPushScheduleService.scheduleTask(
                groupId,
                TASK_TYPE,
                mode.getValue(),
                cronExpression,
                description,
                () -> executePush(groupId, mode.getValue())
        );
    }

    @Override
    public boolean unschedulePush(Long groupId, PixivRankPushMode mode) {
        GroupPushSchedule existing = groupPushScheduleService.getTaskConfig(groupId, TASK_TYPE, mode.getValue());
        if (existing != null) {
            groupPushScheduleService.unscheduleTask(groupId, TASK_TYPE, mode.getValue());
            return true;
        }
        return false;
    }

    @Override
    public List<PixivRankPushSchedule> getSchedulesByGroupId(Long groupId) {
        List<GroupPushSchedule> configs = groupPushScheduleService.listTaskConfigs(groupId, TASK_TYPE);
        return configs.stream().map(this::convertToLegacyDto).collect(Collectors.toList());
    }

    @Override
    public PixivRankPushSchedule getSchedule(Long groupId, PixivRankPushMode mode) {
        GroupPushSchedule config = groupPushScheduleService.getTaskConfig(groupId, TASK_TYPE, mode.getValue());
        if (config == null) {
            return null;
        }
        return convertToLegacyDto(config);
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
     * JobRunr target method
     */
    public void executePush(Long groupId, String mode) {
        groupPushTaskExecutor.execute(groupId, "P站排行榜推送-" + mode, (bot) -> {
            log.info("开始执行P站排行榜推送任务, 群组ID: {}, 类型: {}", groupId, mode);
            pixivRankService.fetchAndPushRank(groupId, PixivRankPushMode.fromValue(mode), PixivRankService.Content.ILLUST);
        });
    }

    private PixivRankPushSchedule convertToLegacyDto(GroupPushSchedule config) {
        PixivRankPushSchedule dto = new PixivRankPushSchedule();
        // Since id is Integer in new entity and Integer in old, we can copy or ignore.
        // Old entity ID was Serial (Integer).
        dto.setId(config.getId());
        dto.setGroupId(config.getGroupId());
        dto.setRankType(config.getTaskParam());
        dto.setCronSchedule(config.getCronExpression());
        dto.setDescription(config.getDescription());
        dto.setCreatedAt(config.getCreatedAt());
        dto.setUpdatedAt(config.getUpdatedAt());
        return dto;
    }
}

