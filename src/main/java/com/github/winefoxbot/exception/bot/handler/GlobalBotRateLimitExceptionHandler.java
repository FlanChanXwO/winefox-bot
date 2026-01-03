package com.github.winefoxbot.exception.bot.handler;

import com.github.winefoxbot.exception.bot.RateLimitException;
import com.mikuac.shiro.core.Bot;
import com.mikuac.shiro.dto.event.message.AnyMessageEvent;
import com.mikuac.shiro.dto.event.message.GroupMessageEvent;
import com.mikuac.shiro.dto.event.message.MessageEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class GlobalBotRateLimitExceptionHandler {

    public void handleRateLimitException(RateLimitException e) {
        MessageEvent event = (MessageEvent) e.getEvent();
        Bot bot = e.getBot();
        if (bot != null && event != null) {
            log.warn("触发限流 - 用户[{}], 事件类型[{}], 消息: {}",
                    event.getUserId(), // getUserId() 在 MessageEvent 中存在
                    event.getClass().getSimpleName(),
                    e.getMessage());
            switch (event) {
                case AnyMessageEvent anyMessageEvent -> bot.sendMsg(anyMessageEvent, e.getMessage(), false);
                case GroupMessageEvent groupMessageEvent ->
                        bot.sendGroupMsg(groupMessageEvent.getGroupId(), e.getMessage(), false);
                default -> bot.sendPrivateMsg(event.getUserId(), e.getMessage(), false);
            }
        } else {
            log.error("RateLimitException被捕获，但缺少Bot或Event上下文，无法发送消息。", e);
        }
    }
}