package com.github.winefoxbot.core.aitools;

import com.github.winefoxbot.core.context.BotContext;
import com.github.winefoxbot.core.service.status.StatusImageService;
import com.mikuac.shiro.common.utils.MsgUtils;
import com.mikuac.shiro.core.Bot;
import com.mikuac.shiro.dto.event.message.AnyMessageEvent;
import com.mikuac.shiro.dto.event.message.MessageEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Description;

import java.io.IOException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.Locale;
import java.util.function.Function;

/**
 * 一个用于获取当前日期和时间的工具。
 * AI可以通过调用此工具来获知实时的时间信息。
 * @author FlanChan
 */
@Configuration(proxyBeanMethods = false)
@Slf4j
@RequiredArgsConstructor
public class StatusTool {
    private final StatusImageService statusImageService;

    /**
     * 获取
     *
     */
    @Bean("statusGetTool")
    @Description("""
    获取当前Bot的状态信息。
    可以通过调用此工具来获取Bot的实时状态信息，例如当前在线状态、系统负载、内存使用情况等。该工具会生成一张包含这些状态信息的图片，并将其发送给用户。用户可以通过询问Bot的状态来触发此工具，例如“你现在状态怎么样？”、“系统负载如何？”等问题。返回的图片将以直观的方式展示Bot的当前状态，帮助用户了解Bot的运行情况。
    该工具不需要任何输入参数，直接调用即可获取当前状态信息的图片。
    """)
    public Function<Void,Boolean> getStatusTool() {
        return _ -> {
            log.info("AI调用获取状态工具");
            Bot bot = BotContext.CURRENT_BOT.get();
            AnyMessageEvent messageEvent = (AnyMessageEvent) BotContext.CURRENT_MESSAGE_EVENT.get();
            try {
                bot.sendMsg(messageEvent, MsgUtils.builder()
                        .img(statusImageService.generateStatusImage())
                        .build(), false);
            } catch (IOException | InterruptedException e) {
                throw new RuntimeException(e);
            }
            return true;
        };
    }
}
