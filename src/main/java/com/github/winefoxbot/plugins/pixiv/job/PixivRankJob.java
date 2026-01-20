package com.github.winefoxbot.plugins.pixiv.job;

import com.github.winefoxbot.core.annotation.schedule.BotTask;
import com.github.winefoxbot.core.model.enums.PushTargetType;
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
        key = "PIXIV_RANK_PUSH",
        name = "P站排行榜群推送",
        description = "定时给群聊推送P站插画排行榜（日榜/周榜/月榜）, 参数为排行榜类型：daily, weekly, monthly",
        paramExample = "daily",
        targetType = PushTargetType.GROUP
)
public class PixivRankJob implements BotJobHandler<String> {

    private final PixivRankService pixivRankService;

    @Override
    public void run(Bot bot, Long targetId, PushTargetType targetType, String parameter) {
        // parameter 存储的是 rankMode 的 value (例如 "daily")
        log.info("开始执行P站排行榜推送任务, 群组ID: {}, 类型: {}", targetId, parameter);

        try {
            PixivRankPushMode mode = PixivRankPushMode.fromValue(parameter);
            // 执行原有的业务逻辑
            pixivRankService.fetchAndPushRank(targetId, mode, PixivRankService.Content.ILLUST);
        } catch (Exception e) {
            log.error("P站排行榜推送任务执行异常", e);
            bot.sendGroupMsg(targetId, "P站排行榜推送执行失败，请检查日志。", false);
        }
    }
}
