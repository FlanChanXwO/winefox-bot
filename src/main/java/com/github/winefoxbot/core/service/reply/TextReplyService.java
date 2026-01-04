package com.github.winefoxbot.core.service.reply;

import com.github.winefoxbot.core.model.dto.TextReply;
import com.github.winefoxbot.core.model.dto.TextReplyParams;

/**
 * @author FlanChan (badapple495@outlook.com)
 * @since 2025-12-08-20:12
 */
public interface TextReplyService {
    TextReply getReply(TextReplyParams params);
}
