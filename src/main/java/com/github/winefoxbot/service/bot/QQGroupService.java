package com.github.winefoxbot.service.bot;

import com.github.winefoxbot.model.dto.GroupEventMessage;
import com.mikuac.shiro.core.Bot;

/**
 * @author FlanChan (badapple495@outlook.com)
 * @since 2025-12-04-21:17
 */
public interface QQGroupService {
    void handleWelcomeMessage(Bot bot, GroupEventMessage eventMessage);

    void handleFarewellMessage(Bot bot, GroupEventMessage eventMessage);


    void handleStopMessage(Bot bot, GroupEventMessage eventMessage);
}
