package com.github.winefoxbot.service.bot;

import com.github.winefoxbot.service.bot.impl.BotReplyServiceImpl;

/**
 * @author FlanChan (badapple495@outlook.com)
 * @since 2025-12-08-20:12
 */
public interface BotReplyService {
    BotReplyServiceImpl.Reply getWelcomeReply(String username);

    BotReplyServiceImpl.Reply getFarewellReply(String username);

    BotReplyServiceImpl.Reply getMasterStopReply(String username);
}
