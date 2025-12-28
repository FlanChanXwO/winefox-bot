package com.github.winefoxbot.aop.interceptor;

import com.github.winefoxbot.aop.handler.BotCommandAuthenticationHandler;
import com.github.winefoxbot.aop.handler.BotReceiveMsgHandler;
import com.mikuac.shiro.core.Bot;
import com.mikuac.shiro.core.BotMessageEventInterceptor;
import com.mikuac.shiro.dto.event.message.MessageEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class BotReceiveMsgInterceptor implements BotMessageEventInterceptor {

    private final BotReceiveMsgHandler botReceiveMsgHandler;
    private final BotCommandAuthenticationHandler botCommandAuthenticationHandler;

    @Override
    public boolean preHandle(Bot bot, MessageEvent event) {
        try {
            if (!botCommandAuthenticationHandler.handle(bot, event)) {
                return false;
            }
            botReceiveMsgHandler.handle(bot, event);
        } catch (Exception e) {
            log.error("Error processing incoming message in BotReceiveMsgHandler", e);
        }
        return true;
    }

    @Override
    public void afterCompletion(Bot bot, MessageEvent event) {
        // This method is called after all plugins have been executed.
        // You can add logic here if you need to perform actions after message processing.
    }
}