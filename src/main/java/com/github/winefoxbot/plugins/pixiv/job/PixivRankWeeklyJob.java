package com.github.winefoxbot.plugins.pixiv.job;

import com.github.winefoxbot.core.annotation.schedule.BotTask;
import com.github.winefoxbot.core.config.plugin.BasePluginConfig;
import com.github.winefoxbot.core.model.enums.common.PushTargetType;
import com.github.winefoxbot.core.service.schedule.handler.BotJobHandler;
import com.github.winefoxbot.plugins.pixiv.model.enums.PixivRankPushMode;
import com.github.winefoxbot.plugins.pixiv.service.PixivRankService;
import com.mikuac.shiro.core.Bot;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * P站排行榜推送任务
 *
 * @author FlanChan
 */
@Slf4j
@RequiredArgsConstructor
@BotTask(
        key = "PIXIV_RANK_GROUP_PUSH_WEEKLY",
        name = "P站每周排行榜群推送",
        description = "定时给群聊推送P站插画每周排行榜",
        targetType = PushTargetType.GROUP
)
public class PixivRankWeeklyJob implements BotJobHandler<String, BasePluginConfig> {

    private final PixivRankService pixivRankService;

    @Override
    public void run(Bot bot, Long targetId, PushTargetType targetType, String parameter) {
        log.info("开始执行P站每周排行榜推送任务, 群组ID: {}", targetId);

        try {
            // 执行原有的业务逻辑
            pixivRankService.fetchAndPushRank(targetId, PixivRankPushMode.WEEKLY, PixivRankService.Content.ILLUST);
        } catch (Exception e) {
            log.error("P站排行榜推送任务执行异常", e);
            bot.sendGroupMsg(targetId, "P站排行榜推送执行失败，请检查日志。", false);
        }
    }
}
