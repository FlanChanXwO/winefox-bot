package com.github.winefoxbot.service.bot;

import com.github.winefoxbot.model.dto.reply.BotReply;
import com.github.winefoxbot.model.dto.reply.BotReplyParams;
import com.github.winefoxbot.service.bot.impl.BotReplyServiceImpl;

/**
 * @author FlanChan (badapple495@outlook.com)
 * @since 2025-12-08-20:12
 */
public interface BotReplyService {
    BotReply getReply(BotReplyParams params);
}
