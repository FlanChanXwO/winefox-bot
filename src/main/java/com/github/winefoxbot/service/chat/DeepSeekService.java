package com.github.winefoxbot.service.chat;

import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * @author FlanChan (badapple495@outlook.com)
 * @since 2025-12-09-17:22
 */
public interface DeepSeekService {


    String complete(Long sessionId, String sessionType, ObjectNode userMsg);

}
