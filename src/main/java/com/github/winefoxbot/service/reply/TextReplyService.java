package com.github.winefoxbot.service.reply;

import com.github.winefoxbot.model.dto.reply.TextReply;
import com.github.winefoxbot.model.dto.reply.TextReplyParams;

/**
 * @author FlanChan (badapple495@outlook.com)
 * @since 2025-12-08-20:12
 */
public interface TextReplyService {
    TextReply getReply(TextReplyParams params);
}
