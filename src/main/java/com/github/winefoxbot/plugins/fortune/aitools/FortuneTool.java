package com.github.winefoxbot.plugins.fortune.aitools;

import com.github.winefoxbot.core.context.BotContext;
import com.github.winefoxbot.core.model.enums.common.MessageType;
import com.github.winefoxbot.core.utils.PluginConfigBinder;
import com.github.winefoxbot.plugins.fortune.config.FortunePluginConfig;
import com.github.winefoxbot.plugins.fortune.model.vo.FortuneRenderVO;
import com.github.winefoxbot.plugins.fortune.service.FortuneDataService;
import com.mikuac.shiro.core.Bot;
import com.mikuac.shiro.core.BotContainer;
import com.mikuac.shiro.dto.event.message.AnyMessageEvent;
import com.mikuac.shiro.dto.event.message.MessageEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Description;

import java.util.Optional;
import java.util.function.Function;

/**
 * AI工具类，用于查询今日运势
 * 当AI认为用户想要查询今日运势时，会调用此工具
 *
 * @author FlanChan
 */
@Configuration(proxyBeanMethods = false)
@RequiredArgsConstructor
@Slf4j
public class FortuneTool {

    private final FortuneDataService fortuneService;
    private final PluginConfigBinder configBinder;

    public record FortuneRequest() {}

    public record FortuneResponse(
            Boolean success,
            String message
    ) {}


    @Bean("fortuneGetTool")
    @Description("""
            获取今日运势信息的工具。
            当用户想要查询今日运势时，应该调用此工具。
            该工具调用后会发送今日运势卡片到用户所在的聊天中，并返回运势的获取结果。
            该工具不需要任何输入参数。
            """)
    public Function<FortuneRequest, FortuneResponse> fortuneGetTool() {
        return _ -> {
            log.info("AI工具调用：获取今日运势");
            Bot bot = BotContext.CURRENT_BOT.get();
            AnyMessageEvent messageEvent = (AnyMessageEvent) BotContext.CURRENT_MESSAGE_EVENT.get();
            try {
                FortunePluginConfig config = FortunePluginConfig.class.getDeclaredConstructor().newInstance();
                configBinder.bind(config, messageEvent.getGroupId(), messageEvent.getUserId());
                BotContext.runWithContext(bot,messageEvent,config, () -> {
                    fortuneService.getFortune(bot, messageEvent);
                });
                return new FortuneResponse(true, "运势获取成功，运势卡片已发送");
            } catch (Exception e) {
                log.error("AI工具获取运势失败", e);
                return new FortuneResponse(false, "运势获取失败");
            }
        };
    }
}
