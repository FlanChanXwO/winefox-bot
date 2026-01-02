package com.github.winefoxbot.service.chat.impl;

import cn.hutool.json.JSONUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.winefoxbot.config.ai.WineFoxBotChatConfig;
import com.github.winefoxbot.model.entity.ShiroUserMessage;
import com.github.winefoxbot.service.chat.AiInteractionHelper;
import com.github.winefoxbot.service.chat.DeepSeekService;
import com.github.winefoxbot.service.shiro.ShiroMessagesService;
import com.github.winefoxbot.utils.BotUtils;
import jakarta.annotation.Resource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
@ConditionalOnBean(name = "deepSeekChatClient")
public class DeepSeekiServiceImpl implements DeepSeekService {
    @Resource(name = "deepSeekChatClient")
    private ChatClient chatClient;
    private final WineFoxBotChatConfig botChatConfig;
    private final ShiroMessagesService shiroMessagesService;
    private final AiInteractionHelper aiInteractionHelper;
    private final ObjectMapper objectMapper;
    private static final int CONTEXT_READ_LIMIT = 200;

    @Override
    public String complete(Long sessionId, String sessionType, ObjectNode userMsg) {
        List<Message> messages = new ArrayList<>();
        messages.add(new SystemMessage(botChatConfig.getSystemPrompt()));

        // --- 核心优化点：使用 Helper 构建历史消息 ---
        List<ShiroUserMessage> history = shiroMessagesService.findLatestMessagesForContext(sessionId, sessionType, CONTEXT_READ_LIMIT);
        for (int i = history.size() - 1; i >= 0; i--) {
            ShiroUserMessage shiroMsg = history.get(i);
            try {
                String filteredText = BotUtils.getFilteredTextMessage(JSONUtil.toJsonStr(shiroMsg.getMessage()));

                // 使用 Helper 创建 ObjectNode
                ObjectNode messageForAI = aiInteractionHelper.createHistoryMessageNode(shiroMsg, filteredText);
                String finalJsonForAI = objectMapper.writeValueAsString(messageForAI);

                boolean isBotMessage = "message_sent".equals(shiroMsg.getDirection());
                if (isBotMessage) {
                    messages.add(new AssistantMessage(finalJsonForAI));
                } else {
                    messages.add(new UserMessage(finalJsonForAI));
                }

            } catch (IOException | NumberFormatException e) {
                log.error("Failed to process history message for AI context: {}", shiroMsg.getMessage(), e);
            }
        }

        // 处理当前用户消息（这部分逻辑不变）
        if (userMsg != null) {
            try {
                String currentUserMessageJson = objectMapper.writeValueAsString(userMsg);
                messages.add(new UserMessage(currentUserMessageJson));
            } catch (JsonProcessingException e) {
                log.error("Failed to serialize current user message for AI context: {}", userMsg, e);
            }
        }

        log.info("Sending {} messages to AI for context.", messages.size());
        return chatClient.prompt(new Prompt(messages)).call().content();
    }
}