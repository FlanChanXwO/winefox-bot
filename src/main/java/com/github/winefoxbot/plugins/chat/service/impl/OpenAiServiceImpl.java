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
import com.mikuac.shiro.common.utils.MessageConverser;
import com.mikuac.shiro.common.utils.ShiroUtils;
import com.mikuac.shiro.core.Bot;
import com.mikuac.shiro.core.BotContainer;
import com.mikuac.shiro.dto.action.common.ActionData;
import com.mikuac.shiro.dto.action.response.MsgResp;
import com.mikuac.shiro.model.ArrayMsg;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.apache.commons.lang3.StringUtils;
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
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
    private final OkHttpClient okHttpClient;
    private final BotContainer botContainer;
    // 用于从异常或残缺的 JSON 中提取 message 字段的正则表达式
    private static final Pattern FALLBACK_MESSAGE_PATTERN = Pattern.compile("\"message\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"");


    @Override
    public String complete(Long sessionId, MessageType messageType, AiMessageInput currentMessage) {
        List<Message> messages = new ArrayList<>();
        Optional<Bot> botOpt = botContainer.robots.values().stream().findFirst();
        SystemMessage systemMessage = new SystemMessage(botChatConfig.getSystemPrompt());
        // 0. 添加系统提示
        messages.add(systemMessage);

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
                        log.debug("History message {} contains images. Re-fetching URLs to prevent expiration.", shiroMsg.getId());

                        List<String> freshImageUrls = new ArrayList<>();
                        // Re-fetch message from API to get non-expired image URLs
                        if (botOpt.isPresent()) {
                            try {
                                Bot bot = botOpt.get();
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
                        } else {
                            log.warn("Bot not available, cannot re-fetch image URLs. The old URLs might be expired.");
                            // Fallback to old URLs, though they might fail
                            freshImageUrls = historyInput.getImageUrls();
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

        // 获取配置的限制
        Prompt prompt = new Prompt(messages);
        int maxTokens = wineFoxBotChatProperties.getMaxContextTokens();
        // 预留一部分 token 给 output (防止输入就占满了)
        int safeLimit = maxTokens - 500;
        log.info("current prompt token estimate: {}, max allowed: {}", prompt.getContents().length(), safeLimit);
        if (prompt.getContents().length() > safeLimit) {
            log.warn("Context tokens ({}) exceed the limit ({}). Truncating oldest messages.", prompt.getContents().length(), safeLimit);
            throw new IllegalStateException("对话上下文过长，已超出限制，请尝试减少图片或消息数量后重试。");
        }

        log.info("Sending {} messages to AI for context.", messages.size());
        // 调用 AI
        String rawResponse = chatClient.prompt(prompt).call().content();
        // 清洗 AI 的回复，防止它输出 JSON
        return cleanAiResponse(rawResponse);
    }

    /**
     * 简单的 Token 估算器
     * 由于 Gemini 对多语言 Token 处理较好，且我们是做预算控制而非精确截断，
     * 这里使用保守策略：假设 1 个字符 = 1 个 Token (中文通常 >1 char/token，英文 <1 char/token)
     * 这样能确保不超支。
     */
    private int estimateTokenCount(String text) {
        if (StringUtils.isBlank(text)) {
            return 0;
        }
        return text.length();
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


    /**
     * 清洗 AI 回复。如果 AI 抽风输出了 JSON 或 Markdown 包裹的 JSON，尝试提取 message 字段。
     */
    private String cleanAiResponse(String content) {
        if (StringUtils.isBlank(content)) {
            return "";
        }
        String trimmed = content.trim();

        // 尝试寻找最外层的 JSON 对象范围（从第一个 { 到最后一个 }）
        // 这样可以防御性地处理：
        // 1. 纯 JSON
        // 2. Markdown 代码块包裹的 JSON (```json { ... } ```)
        // 3. 混杂在文字中的 JSON
        int start = trimmed.indexOf('{');
        int end = trimmed.lastIndexOf('}');

        if (start >= 0 && end > start) {
            String potentialJson = trimmed.substring(start, end + 1);
            try {
                // 尝试解析
                JSONObject json = JSONUtil.parseObj(potentialJson);
                if (json.containsKey("message")) {
                    log.warn("AI 输出了结构化数据，已自动提取 message 字段。");
                    return json.getStr("message");
                }
            } catch (Exception e) {
                // 解析失败，说明可能只是普通的包含花括号的对话，或者 JSON 不完整 (half JSON)
                // 这种情况下我们无法准确修复，只能忽略异常回退到返回原始文本
            }
        }


        // 2. 兜底策略：正则暴力提取
        // 针对场景：'{"sender":"bot",...,"message":"内容在此 (缺失结尾...
        // 这种数据没有结尾的括号，JSONUtil 肯定会报错，但内容其实都在
        Matcher matcher = FALLBACK_MESSAGE_PATTERN.matcher(trimmed);
        if (matcher.find()) {
            String extractedValue = matcher.group(1);
            try {
                // 利用 JSONUtil 的能力去把转义字符(e.g. \n, \") 正确还原
                // 我们构造一个临时的最小合法 JSON 字符串进行反序列化，这比手写 replace 更安全
                String tempJsonStr = "{\"tempContent\": \"" + extractedValue + "\"}";
                JSONObject tempJson = JSONUtil.parseObj(tempJsonStr);
                log.warn("检测到残缺/异常的 JSON 数据，已通过正则强制提取 message 字段。");
                return tempJson.getStr("tempContent");
            } catch (Exception e) {
                // 如果反转义也失败了，至少返回正则提取出来的原始字符串，好过把一大坨 JSON 抛给用户
                return extractedValue;
            }
        }

        // 3. 既不是 JSON 也匹配不到 message 字段，直接返回原文
        return trimmed;
    }
}