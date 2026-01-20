package com.github.winefoxbot.core.listener;

import com.github.winefoxbot.core.event.BotOfflineEvent;
import com.github.winefoxbot.core.event.BotOnlineEvent;
import com.mikuac.shiro.core.Bot;
import com.mikuac.shiro.core.CoreEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * @author FlanChan
 */
@Primary
@Component
@RequiredArgsConstructor
@Slf4j
public class BotStatusEventListener extends CoreEvent {

    private final ApplicationEventPublisher eventPublisher;


    @Override
    public void online(Bot bot) {
        eventPublisher.publishEvent(new BotOnlineEvent(this, bot));
    }

    @Override
    public void offline(long account) {
        eventPublisher.publishEvent(new BotOfflineEvent(this, account));
    }

}
