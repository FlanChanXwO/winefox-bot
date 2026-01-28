package com.github.winefoxbot.plugins.chat.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.winefoxbot.core.context.BotContext;
import com.github.winefoxbot.core.model.entity.ShiroUserMessage;
import com.github.winefoxbot.core.model.enums.common.MessageDirection;
import com.github.winefoxbot.core.model.enums.common.MessageType;
import com.github.winefoxbot.core.service.shiro.ShiroMessagesService;
import com.github.winefoxbot.core.utils.BotUtils;
import com.github.winefoxbot.plugins.chat.config.WineFoxBotChatProperties;
import com.github.winefoxbot.plugins.chat.service.AiInteractionHelper;
import com.github.winefoxbot.plugins.chat.service.AiInteractionHelper.AiMessageInput;
import com.github.winefoxbot.plugins.chat.service.OpenAiService;
import com.mikuac.shiro.common.utils.MessageConverser;
import com.mikuac.shiro.common.utils.ShiroUtils;
import com.mikuac.shiro.core.Bot;
import com.mikuac.shiro.dto.action.common.ActionData;
import com.mikuac.shiro.dto.action.response.MsgResp;
import com.mikuac.shiro.dto.event.message.AnyMessageEvent;
import com.mikuac.shiro.model.ArrayMsg;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.content.Media;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.stereotype.Service;
import org.springframework.util.MimeTypeUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * @author FlanChan
 */
@Service
@Slf4j
@RequiredArgsConstructor
@ConditionalOnBean(ChatClient.class)
public class OpenAiServiceImpl implements OpenAiService {
    private final ChatClient chatClient;
    private final ShiroMessagesService shiroMessagesService;
    private final AiInteractionHelper aiInteractionHelper;
    private final ObjectMapper objectMapper;
    private final WineFoxBotChatProperties wineFoxBotChatProperties;
    private final OkHttpClient okHttpClient;


    @Override
    public String complete(AiMessageInput currentMessage) {
        List<Message> messages = new ArrayList<>();
        Bot bot = BotContext.CURRENT_BOT.get();
        // 1. 处理历史记录
        AnyMessageEvent messageEvent = (AnyMessageEvent) BotContext.CURRENT_MESSAGE_EVENT.get();
        Long sessionId = BotUtils.getSessionId(messageEvent);
        MessageType messageType = MessageType.fromValue(messageEvent.getMessageType());
        List<ShiroUserMessage> history = shiroMessagesService.findLatestMessagesForContext(sessionId, messageType, wineFoxBotChatProperties.getContextSize());
        for (int i = history.size() - 1; i >= 0; i--) {
            ShiroUserMessage shiroMsg = history.get(i);
            try {
                // 使用 Helper 解析历史消息（包含图片提取）
                AiMessageInput historyInput = aiInteractionHelper.createHistoryMessageInput(shiroMsg);
                String historyMessage = objectMapper.writeValueAsString(historyInput.getTextContent());
                boolean isBotMessage = MessageDirection.MESSAGE_SENT.equals(shiroMsg.getDirection());
                if (isBotMessage) {
                    messages.add(new AssistantMessage(historyMessage));
                } else {
                    // 如果历史记录里有图片，也需要添加
                    // 前提是图片分析功能开启
                    if (wineFoxBotChatProperties.getEnableImageAnalysis() && historyInput.getImageUrls() != null && !historyInput.getImageUrls().isEmpty()) {
                        log.debug("History message {} contains images. Re-fetching URLs to prevent expiration.", shiroMsg.getId());

                        List<String> freshImageUrls = new ArrayList<>();
                        try {
                            ActionData<MsgResp> msgResp = bot.getMsg(shiroMsg.getMessageId().intValue());
                            MsgResp msgData = Optional.ofNullable(msgResp).map(ActionData::getData).orElse(null);
                            if (msgData != null && msgData.getMessage() != null) {
                                List<ArrayMsg> arrayMsgs = MessageConverser.stringToArray(msgData.getMessage());
                                freshImageUrls = ShiroUtils.getMsgImgUrlList(arrayMsgs);
                                log.debug("Successfully re-fetched {} fresh image URLs for message {}.", freshImageUrls.size(), shiroMsg.getId());
                            } else {
                                log.warn("Could not re-fetch message data for message ID: {}. Response was null or empty.", shiroMsg.getMessageId());
                            }
                        } catch (Exception e) {
                            log.error("Failed to re-fetch image URLs for historical message {}", shiroMsg.getMessageId(), e);
                        }

                        List<Media> mediaList = convertUrlsToMedia(freshImageUrls);
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
            List<Media> mediaList = convertUrlsToMedia(currentMessage.getImageUrls());
            if (!mediaList.isEmpty()) {
                messages.add(UserMessage.builder()
                        .text(currentMessage.getTextContent())
                        .media(mediaList)
                        .build());
            } else {
                messages.add(new UserMessage(currentMessage.getTextContent()));
            }
        }

        Prompt prompt = new Prompt(messages);
        log.info("Sending {} messages to AI.", messages.size());
        return chatClient.prompt(prompt).call().content();
    }


    /**
     * 将 URL 字符串列表转换为 Spring AI 的 Media 对象列表
     * Uses OkHttpClient to download the image and wraps it in a ByteArrayResource.
     */
    private List<Media> convertUrlsToMedia(List<String> imageUrls) {
        if (imageUrls == null || imageUrls.isEmpty()) {
            return Collections.emptyList();
        }

        List<Media> mediaList = new ArrayList<>();
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
}