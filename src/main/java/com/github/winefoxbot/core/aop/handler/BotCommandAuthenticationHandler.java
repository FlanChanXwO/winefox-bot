package com.github.winefoxbot.core.aop.handler;

import com.mikuac.shiro.core.Bot;
import com.mikuac.shiro.dto.event.message.MessageEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class BotCommandAuthenticationHandler {

    public boolean handle(Bot bot, MessageEvent event) {
        return true;
    }


}