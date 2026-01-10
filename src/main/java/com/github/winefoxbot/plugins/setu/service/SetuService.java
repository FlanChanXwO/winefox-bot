package com.github.winefoxbot.plugins.setu.service;

import com.mikuac.shiro.core.Bot;
import com.mikuac.shiro.dto.event.message.AnyMessageEvent;

/**
 * @author FlanChan (badapple495@outlook.com)
 * @since 2026-01-09-8:46
 */
public interface SetuService {
    void processSetuRequest(Bot bot, AnyMessageEvent event, String tag);

    void processSetuRequest(Bot bot, Long userId, Long groupId, String tag);
}
