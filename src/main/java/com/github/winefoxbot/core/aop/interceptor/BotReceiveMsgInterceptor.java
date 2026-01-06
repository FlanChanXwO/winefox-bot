package com.github.winefoxbot.core.aop.interceptor;

import com.github.winefoxbot.core.aop.handler.BotReceiveMsgHandler;
import com.mikuac.shiro.core.Bot;
import com.mikuac.shiro.core.BotMessageEventInterceptor;
import com.mikuac.shiro.dto.event.message.MessageEvent;
import com.mikuac.shiro.exception.ShiroException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class BotReceiveMsgInterceptor implements BotMessageEventInterceptor {

    private final BotReceiveMsgHandler botReceiveMsgHandler;

    @Override
    public boolean preHandle(Bot bot, MessageEvent event) {return true;}

    @Override
    public void afterCompletion(Bot bot, MessageEvent event) throws ShiroException {
        botReceiveMsgHandler.handle(bot, event);
    }
}