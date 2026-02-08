package com.github.winefoxbot.plugins.dailyreport.aitools;

import com.github.winefoxbot.core.context.BotContext;
import com.github.winefoxbot.core.utils.PluginConfigBinder;
import com.github.winefoxbot.plugins.dailyreport.service.DailyReportService;
import com.github.winefoxbot.plugins.setu.config.SetuPluginConfig;
import com.mikuac.shiro.common.utils.MsgUtils;
import com.mikuac.shiro.core.Bot;
import com.mikuac.shiro.dto.event.message.AnyMessageEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Description;

import java.io.IOException;
import java.util.function.Function;

/**
 * AI工具类，用于调用DailyReportPlugin日报功能
 * 当AI认为用户想要查看酒狐日报时，会调用此工具
 *
 * @author FlanChan
 */
@Configuration(proxyBeanMethods = false)
@RequiredArgsConstructor
@Slf4j
public class DailyReportTool {

    private final DailyReportService dailyReportService;
    private final PluginConfigBinder configBinder;

    @Bean("dailyReportGetTool")
    @Description("""
            获取酒狐日报图片的工具，酒狐日报包含每日的新番信息、60S看世界、IT资讯，以及中国国内的节假日倒计时，以及B站热搜，还有今日一言信息。
            当用户明确想要查看酒狐日报时，应该调用此工具。
            该工具调用后会发送酒狐日报图片到用户所在的聊天中，并返回日报的获取结果。
            该工具不需要任何输入参数。
            """)
    public Function<Void, Boolean> dailyReportGetTool() {
        return _ -> {
            log.info("AI调用酒狐日报工具");
            try {
                Bot bot = BotContext.CURRENT_BOT.get();
                AnyMessageEvent messageEvent = (AnyMessageEvent) BotContext.CURRENT_MESSAGE_EVENT.get();
                SetuPluginConfig config = SetuPluginConfig.class.getDeclaredConstructor().newInstance();
                configBinder.bind(config, messageEvent.getGroupId(), messageEvent.getUserId());
                BotContext.runWithContext(bot, messageEvent, config, () -> {
                    try {
                        byte[] dailyReportImage = dailyReportService.getDailyReportImage();
                        bot.sendMsg(messageEvent, MsgUtils.builder().img(dailyReportImage).build(), false);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                });
                return true;
            } catch (Exception e) {
                log.error("网络异常，获取随机收藏失败: {}", e.getMessage(), e);
                return false;
            }
        };
    }

}
