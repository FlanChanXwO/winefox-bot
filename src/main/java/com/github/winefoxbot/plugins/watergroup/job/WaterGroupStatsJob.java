package com.github.winefoxbot.plugins.watergroup.job;

import com.github.winefoxbot.core.annotation.schedule.BotTask;
import com.github.winefoxbot.core.context.BotContext;
import com.github.winefoxbot.core.model.enums.PushTargetType;
import com.github.winefoxbot.core.service.schedule.handler.BotJobHandler;
import com.github.winefoxbot.core.utils.FileUtil;
import com.github.winefoxbot.plugins.watergroup.config.WaterGroupPluginConfig;
import com.github.winefoxbot.plugins.watergroup.model.entity.WaterGroupMessageStat;
import com.github.winefoxbot.plugins.watergroup.service.WaterGroupPosterDrawService;
import com.github.winefoxbot.plugins.watergroup.service.WaterGroupService;
import com.mikuac.shiro.common.utils.MsgUtils;
import com.mikuac.shiro.core.Bot;
import com.mikuac.shiro.dto.event.message.GroupMessageEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * 群发言统计推送任务
 *
 * @author FlanChan
 */
@Slf4j
@RequiredArgsConstructor
@BotTask(
        key = "WATER_GROUP_STATS",
        name = "发言统计群推送",
        description = "每日统计群内发言排名并生成海报推送",
        targetType = PushTargetType.GROUP
)
public class WaterGroupStatsJob implements BotJobHandler<String, WaterGroupPluginConfig> {

    private final WaterGroupService waterGroupService;
    private final WaterGroupPosterDrawService waterGroupPosterDrawService;
    private final WaterGroupPluginConfig pluginConfig;

    @Override
    public WaterGroupPluginConfig getPluginConfig() {
        return pluginConfig;
    }

    @Override
    public void run(Bot bot, Long targetId, PushTargetType targetType, String parameter) {
        log.info("开始执行群发言统计推送，群号: {}", targetId);

        List<WaterGroupMessageStat> ranks = waterGroupService.getDailyRanking(targetId);
        if (ranks.isEmpty()) {
            bot.sendGroupMsg(targetId, "今日群内没有足够的数据生成统计~", false);
            return;
        }

        File image = null;
        try {
            // 生成海报
            image = waterGroupPosterDrawService.drawPoster(ranks);

            // 发送提示语
            bot.sendGroupMsg(targetId, "那么，这是今天的活跃榜~", false);

            // 发送图片
            String imgUrl = FileUtil.getFileUrlPrefix() + image.getAbsolutePath();
            bot.sendGroupMsg(targetId, MsgUtils.builder().img(imgUrl).build(), false);

            log.info("群发言统计推送成功，群号: {}", targetId);
        } catch (IOException e) {
            log.error("生成发言统计图片失败，群号: {}", targetId, e);
            bot.sendGroupMsg(targetId, "生成发言统计图片失败，请稍后再试。", false);
        } finally {
            // 清理临时文件
            if (image != null && image.exists()) {
                if (!image.delete()) {
                    log.warn("临时文件删除失败: {}", image.getAbsolutePath());
                }
            }
        }


    }
}
