package com.github.winefoxbot.plugins.chat.service;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.winefoxbot.core.model.enums.MessageType;

/**
 * @author FlanChan (badapple495@outlook.com)
 * @since 2025-12-09-17:22
 */
public interface OpenAiService {


    String complete(Long sessionId, MessageType messageType, ObjectNode userMsg);
}
