package com.github.winefoxbot.plugins.chat.service;

import com.github.winefoxbot.core.model.enums.common.MessageType;

/**
 * @author FlanChan (badapple495@outlook.com)
 * @since 2025-12-09-17:22
 */
public interface OpenAiService {

    /**
     * 发送消息给 AI 进行补全
     * @param sessionId 会话ID (群号或QQ号)
     * @param messageType 消息类型
     * @param currentMessage 当前用户的消息输入（包含文本JSON和可能的图片）
     * @return AI 回复
     */
    String complete(Long sessionId, MessageType messageType, AiInteractionHelper.AiMessageInput currentMessage);
}
