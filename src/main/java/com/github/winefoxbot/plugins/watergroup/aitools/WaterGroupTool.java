package com.github.winefoxbot.plugins.watergroup.aitools;

import com.github.winefoxbot.core.context.BotContext;
import com.github.winefoxbot.core.utils.FileUtil;
import com.github.winefoxbot.core.utils.PluginConfigBinder;
import com.github.winefoxbot.plugins.watergroup.config.WaterGroupPluginConfig;
import com.github.winefoxbot.plugins.watergroup.model.entity.WaterGroupMessageStat;
import com.github.winefoxbot.plugins.watergroup.service.WaterGroupPosterDrawService;
import com.github.winefoxbot.plugins.watergroup.service.WaterGroupService;
import com.mikuac.shiro.common.utils.MsgUtils;
import com.mikuac.shiro.core.Bot;
import com.mikuac.shiro.dto.event.message.AnyMessageEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Description;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.function.Function;

/**
 * AI工具类，用于调用WaterGroupPlugin水群统计功能
 * 当AI认为用户想要查看今日或昨日的水群统计时，会调用此工具
 *
 * @author FlanChan
 */
@Configuration(proxyBeanMethods = false)
@RequiredArgsConstructor
@Slf4j
public class WaterGroupTool {

    private final WaterGroupService waterGroupService;
    private final WaterGroupPosterDrawService waterGroupPosterDrawService;
    private final PluginConfigBinder configBinder;

    @Bean("waterGroupDailyStatsTool")
    @Description("""
            获取今日水群统计图片的工具。
            当用户明确想要查看今日的群发言统计、水群排行榜时，应该调用此工具。
            该工具调用后会发送今日水群统计图片到用户所在的聊天中，并返回获取结果。
            该工具不需要任何输入参数。
            """)
    public Function<Void, Boolean> waterGroupDailyStatsTool() {
        return _ -> {
            log.info("AI调用今日水群统计工具");
            try {
                Bot bot = BotContext.CURRENT_BOT.get();
                AnyMessageEvent messageEvent = (AnyMessageEvent) BotContext.CURRENT_MESSAGE_EVENT.get();
                Long groupId = messageEvent.getGroupId();
                
                if (groupId == null) {
                    bot.sendMsg(messageEvent, "该功能仅限群聊使用", false);
                    return false;
                }

                WaterGroupPluginConfig config = new WaterGroupPluginConfig();
                configBinder.bind(config, groupId, messageEvent.getUserId());
                
                BotContext.runWithContext(bot, messageEvent, config, () -> {
                    List<WaterGroupMessageStat> ranks = waterGroupService.getDailyRanking(groupId);
                    if (ranks.isEmpty()) {
                        bot.sendMsg(messageEvent, "没有足够的数据生成统计", false);
                        return;
                    }
                    bot.sendMsg(messageEvent, "正在生成今日发言统计图片", false);

                    File image = null;
                    try {
                        image = waterGroupPosterDrawService.drawPoster(ranks);
                        bot.sendMsg(messageEvent, MsgUtils.builder()
                                .img(FileUtil.getFileUrlPrefix() + image.getAbsolutePath())
                                .build(), false);
                    } catch (IOException e) {
                        log.error("生成发言统计图片失败", e);
                        bot.sendMsg(messageEvent, "生成发言统计图片失败，请稍后再试。", false);
                    } finally {
                        if (image != null && image.exists()) {
                            if (image.delete()) {
                                log.debug("临时文件删除成功: {}", image.getAbsolutePath());
                            } else {
                                log.warn("临时文件删除失败: {}", image.getAbsolutePath());
                            }
                        }
                    }
                });
                return true;
            } catch (Exception e) {
                log.error("获取今日水群统计失败: {}", e.getMessage(), e);
                return false;
            }
        };
    }

    @Bean("waterGroupYesterdayStatsTool")
    @Description("""
            获取昨日水群统计图片的工具。
            当用户明确想要查看昨日的群发言统计、水群排行榜时，应该调用此工具。
            该工具调用后会发送昨日水群统计图片到用户所在的聊天中，并返回获取结果。
            该工具不需要任何输入参数。
            """)
    public Function<Void, Boolean> waterGroupYesterdayStatsTool() {
        return _ -> {
            log.info("AI调用昨日水群统计工具");
            try {
                Bot bot = BotContext.CURRENT_BOT.get();
                AnyMessageEvent messageEvent = (AnyMessageEvent) BotContext.CURRENT_MESSAGE_EVENT.get();
                Long groupId = messageEvent.getGroupId();

                if (groupId == null) {
                    bot.sendMsg(messageEvent, "该功能仅限群聊使用", false);
                    return false;
                }

                WaterGroupPluginConfig config = new WaterGroupPluginConfig();
                configBinder.bind(config, groupId, messageEvent.getUserId());

                BotContext.runWithContext(bot, messageEvent, config, () -> {
                    List<WaterGroupMessageStat> ranks = waterGroupService.getYesterdayRanking(groupId);
                    if (ranks.isEmpty()) {
                        bot.sendMsg(messageEvent, "没有足够的数据生成统计", false);
                        return;
                    }
                    bot.sendMsg(messageEvent, "正在生成昨日发言统计图片", false);

                    File image = null;
                    try {
                        image = waterGroupPosterDrawService.drawPoster(ranks);
                        bot.sendMsg(messageEvent, MsgUtils.builder()
                                .img(FileUtil.getFileUrlPrefix() + image.getAbsolutePath())
                                .build(), false);
                    } catch (IOException e) {
                        log.error("生成发言统计图片失败", e);
                        bot.sendMsg(messageEvent, "生成发言统计图片失败，请稍后再试。", false);
                    } finally {
                        if (image != null && image.exists()) {
                            if (image.delete()) {
                                log.debug("临时文件删除成功: {}", image.getAbsolutePath());
                            } else {
                                log.warn("临时文件删除失败: {}", image.getAbsolutePath());
                            }
                        }
                    }
                });
                return true;
            } catch (Exception e) {
                log.error("获取昨日水群统计失败: {}", e.getMessage(), e);
                return false;
            }
        };
    }
}
