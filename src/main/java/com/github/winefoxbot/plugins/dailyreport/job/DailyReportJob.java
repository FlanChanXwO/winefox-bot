package com.github.winefoxbot.plugins.dailyreport.job;

import com.github.winefoxbot.core.annotation.schedule.BotTask;
import com.github.winefoxbot.core.config.plugin.BasePluginConfig;
import com.github.winefoxbot.core.model.enums.common.PushTargetType;
import com.github.winefoxbot.core.service.schedule.handler.BotJobHandler;
import com.github.winefoxbot.plugins.dailyreport.service.DailyReportService;
import com.mikuac.shiro.common.utils.MsgUtils;
import com.mikuac.shiro.core.Bot;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;

/**
 * 酒狐日报推送任务
 *
 * @author FlanChan
 */
@Slf4j
@RequiredArgsConstructor
@BotTask(
        key = "DAILY_REPORT",
        name = "酒狐日报群推送",
        description = "每日定时生成并给群聊推送酒狐日报图片",
        targetType = PushTargetType.GROUP
)
public class DailyReportJob implements BotJobHandler<String, BasePluginConfig> {

    private final DailyReportService dailyReportService;

    @Override
    public void run(Bot bot, Long targetId, PushTargetType targetType,  String parameter) {
        log.info("开始执行酒狐日报推送任务，目标: {}", targetId);
        try {
            // 获取日报图片数据
            byte[] image = dailyReportService.getDailyReportImage();

            // 先发送文字提示
            bot.sendGroupMsg(targetId, "今日的酒狐早报来啦~", false);

            // 发送图片
            bot.sendGroupMsg(targetId, MsgUtils.builder().img(image).build(), false);

            log.info("酒狐日报推送成功，目标: {}", targetId);
        } catch (IOException e) {
            log.error("自动推送酒狐日报失败，目标: {}", targetId, e);
            bot.sendGroupMsg(targetId, "酒狐日报生成失败了，请联系管理员查看后台日志。", false);
        }
    }
}
