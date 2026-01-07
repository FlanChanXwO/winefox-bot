package com.github.winefoxbot.plugins.chat.service.impl;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.winefoxbot.core.model.entity.ShiroUserMessage;
import com.github.winefoxbot.core.model.enums.MessageDirection;
import com.github.winefoxbot.core.model.enums.MessageType;
import com.github.winefoxbot.core.service.shiro.ShiroMessagesService;
import com.github.winefoxbot.plugins.chat.config.WineFoxBotChatConfig;
import com.github.winefoxbot.plugins.chat.config.WineFoxBotChatProperties;
import com.github.winefoxbot.plugins.chat.service.AiInteractionHelper;
import com.github.winefoxbot.plugins.chat.service.AiInteractionHelper.AiMessageInput;
import com.github.winefoxbot.plugins.chat.service.OpenAiService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.content.Media;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.stereotype.Service;
import org.springframework.util.MimeTypeUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * @author FlanChan
 */
@Service
@Slf4j
@RequiredArgsConstructor
@ConditionalOnBean(ChatClient.class)
public class OpenAiServiceImpl implements OpenAiService {
    private final ChatClient chatClient;
    private final WineFoxBotChatConfig botChatConfig;
    private final ShiroMessagesService shiroMessagesService;
    private final AiInteractionHelper aiInteractionHelper;
    private final ObjectMapper objectMapper;
    private final WineFoxBotChatProperties wineFoxBotChatProperties;
    private final OkHttpClient okHttpClient; // Using OkHttpClient as requested

    @Override
    public String complete(Long sessionId, MessageType messageType, AiMessageInput currentMessage) {
        List<Message> messages = new ArrayList<>();
        messages.add(new SystemMessage(botChatConfig.getSystemPrompt()));
        // 1. 处理历史记录
        List<ShiroUserMessage> history = shiroMessagesService.findLatestMessagesForContext(sessionId, messageType, wineFoxBotChatProperties.getContextSize());
        for (int i = history.size() - 1; i >= 0; i--) {
            ShiroUserMessage shiroMsg = history.get(i);
            try {
                // 使用 Helper 解析历史消息（包含图片提取）
                AiMessageInput historyInput = aiInteractionHelper.createHistoryMessageInput(shiroMsg);
                String historyMessage = objectMapper.writeValueAsString(historyInput.getContentNode());
                boolean isBotMessage = MessageDirection.MESSAGE_SENT.equals(shiroMsg.getDirection());
                if (isBotMessage) {
                    messages.add(new AssistantMessage(historyMessage));
                } else {
                    // 如果历史记录里有图片，也需要添加
                    // 前提是图片分析功能开启
                    if (wineFoxBotChatProperties.getEnableImageAnalysis() && historyInput.getImageUrls() != null && !historyInput.getImageUrls().isEmpty()) {
                        log.debug("History message {} contains {} images.", shiroMsg.getId(), historyInput.getImageUrls().size());
                        List<Media> mediaList = convertUrlsToMedia(historyInput.getImageUrls());
                        messages.add(UserMessage.builder()
                                .text(historyMessage)
                                .media(mediaList)
                                .build());
                    } else {
                        messages.add(new UserMessage(historyMessage));
                    }
                }

            } catch (JsonProcessingException e) {
                log.error("Failed to process history message JSON: {}", shiroMsg.getId(), e);
            } catch (Exception e) {
                log.error("Error processing history message: {}", shiroMsg.getId(), e);
            }
        }

        log.debug("Loaded {} historical messages for AI context.", history.size());
        // 2. 处理当前用户消息
        if (currentMessage != null) {
            try {
                String currentUserMessageJson = objectMapper.writeValueAsString(currentMessage.getContentNode());

                List<Media> mediaList = convertUrlsToMedia(currentMessage.getImageUrls());

                if (!mediaList.isEmpty()) {
                    log.info("Current message contains {} images.", mediaList.size());
                    messages.add(UserMessage.builder()
                            .text(currentUserMessageJson)
                            .media(mediaList)
                            .build());
                } else {
                    messages.add(new UserMessage(currentUserMessageJson));
                }
            } catch (JsonProcessingException e) {
                log.error("Failed to serialize current user message for AI context.", e);
            }
        }

        log.info("Sending {} messages to AI for context.", messages.size());

        // 调用 AI
        String rawResponse = chatClient.prompt(new Prompt(messages)).call().content();

        // 清洗 AI 的回复，防止它输出 JSON
        return cleanAiResponse(rawResponse);
    }

    /**
     * 将 URL 字符串列表转换为 Spring AI 的 Media 对象列表
     * Uses OkHttpClient to download the image and wraps it in a ByteArrayResource.
     */
    private List<Media> convertUrlsToMedia(List<String> imageUrls) {
        List<Media> mediaList = new ArrayList<>();
        if (imageUrls == null) {
            return mediaList;
        }

        for (String url : imageUrls) {
            Request request = new Request.Builder()
                    .url(url)
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36")
                    .build();

            try (Response response = okHttpClient.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    log.warn("Failed to download image from URL: {}. Server responded with code: {} and message: {}", url, response.code(), response.message());
                    continue;
                }

                ResponseBody body = response.body();
                if (body == null) {
                    log.warn("Failed to download image from URL: {}. Response body was null.", url);
                    continue;
                }

                byte[] imageBytes = body.bytes();
                ByteArrayResource resource = new ByteArrayResource(imageBytes);

                // Use the constructor that accepts a Resource
                mediaList.add(new Media(MimeTypeUtils.IMAGE_JPEG, resource));

            } catch (IOException e) {
                log.error("IOException while downloading image from URL: {}", url, e);
            } catch (IllegalArgumentException e) {
                log.warn("Invalid image URL found in message: {}", url, e);
            }
        }
        return mediaList;
    }


    /**
     * 清洗 AI 回复。如果 AI 抽风输出了 JSON，尝试提取 message 字段。
     */
    private String cleanAiResponse(String content) {
        if (content == null) {
            return "";
        }
        String trimmed = content.trim();

        // 简单判断是否像 JSON 对象
        if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
            try {
                // 尝试解析
                JSONObject json = JSONUtil.parseObj(trimmed);
                if (json.containsKey("message")) {
                    log.warn("AI 输出了 JSON 格式，已自动提取 message 字段。原始输出: {}", trimmed);
                    return json.getStr("message");
                }
            } catch (Exception e) {
                // 解析失败，说明可能只是普通的包含花括号的对话，忽略异常直接返回原文
            }
        }
        return trimmed;
    }
}