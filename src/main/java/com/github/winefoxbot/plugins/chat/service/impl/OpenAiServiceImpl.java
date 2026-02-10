package com.github.winefoxbot.plugins.chat.service.impl;

import com.github.winefoxbot.core.context.BotContext;
import com.github.winefoxbot.core.model.entity.ShiroUserMessage;
import com.github.winefoxbot.core.model.enums.common.MessageDirection;
import com.github.winefoxbot.core.model.enums.common.MessageType;
import com.github.winefoxbot.core.service.shiro.ShiroMessagesService;
import com.github.winefoxbot.core.util.BotUtil;
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
import com.mikuac.shiro.dto.event.message.MessageEvent;
import com.mikuac.shiro.enums.MsgTypeEnum;
import com.mikuac.shiro.model.ArrayMsg;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.content.Media;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.util.MimeTypeUtils;

import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

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
    private final WineFoxBotChatProperties wineFoxBotChatProperties;


    @Override
    public String complete(AiMessageInput currentMessage) {
        List<Message> messages = new ArrayList<>();
        Bot bot = BotContext.CURRENT_BOT.get();
        // 1. 处理历史记录
        AnyMessageEvent messageEvent = (AnyMessageEvent) BotContext.CURRENT_MESSAGE_EVENT.get();

        Long sessionId = BotUtil.getSessionId(messageEvent);
        MessageType messageType = MessageType.fromValue(messageEvent.getMessageType());
        List<ShiroUserMessage> history = new ArrayList<>(shiroMessagesService.findLatestMessagesForContext(sessionId, messageType, wineFoxBotChatProperties.getContextSize()));
        Optional<ArrayMsg> replyInfo = messageEvent.getArrayMsg().stream().filter(e -> MsgTypeEnum.reply.equals(e.getType())).findFirst();
        if (replyInfo.isPresent()) {
            try {
                int replyMsgId = replyInfo.get().getData().get("id").asInt();
                boolean alreadyInHistory = history.stream().anyMatch(h -> h.getMessageId() == replyMsgId);
                if (!alreadyInHistory) {
                    log.debug("Current message is a reply to {}. Fetching original message to add to context.", replyMsgId);
                    // 注意: 此处假设 ShiroMessagesService 提供了 findByMessageId 方法
                    ShiroUserMessage repliedMsg = shiroMessagesService.findByMessageId((long) replyMsgId);
                    if (repliedMsg != null) {
                        history.add(repliedMsg); // 添加到末尾，循环时会先处理
                    } else {
                        log.warn("Could not find replied message with ID {} in database.", replyMsgId);
                    }
                }
            } catch (Exception e) {
                log.error("Failed to process reply information.", e);
            }
        }

        for (int i = history.size() - 1; i >= 0; i--) {
            ShiroUserMessage shiroMsg = history.get(i);
            try {
                processHistoryMessage(messages, shiroMsg, bot);
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
        return cleanResponse(chatClient.prompt(prompt).call().content());
    }

    private void processHistoryMessage(List<Message> messages, ShiroUserMessage shiroMsg, Bot bot) {
        // 使用 Helper 解析历史消息（包含图片提取）
        AiMessageInput historyInput = aiInteractionHelper.createHistoryMessageInput(shiroMsg);
        String historyMessage = historyInput.getTextContent();
        boolean isBotMessage = MessageDirection.MESSAGE_SENT.equals(shiroMsg.getDirection());

        // 始终重新获取图片URL，以防链接过期或初始解析不完整
        List<String> imageUrls = getFreshImageUrls(shiroMsg, bot);
        List<Media> mediaList = convertUrlsToMedia(imageUrls);

        if (isBotMessage) {
            // Bot消息处理
            if (!mediaList.isEmpty()) {
                historyMessage += "\n[发送了图片]";
            }
            messages.add(new AssistantMessage(historyMessage));

            // CRITICAL FIX: 如果 Bot 发送了图片，通过插入一个 System/User 代理消息让 AI 能看到这张图片
            // 因为 AssistantMessage 不支持直接携带 Media，所以如果 AI 需要"看见"自己发的图，必须通过这种 Hack 方式
            if (!mediaList.isEmpty()) {
                messages.add(UserMessage.builder()
                        .text("[System: 这是上一条酒狐发送的图片，请知悉]")
                        .media(mediaList)
                        .build());
            }
        } else {
            // 用户消息处理
            if (!mediaList.isEmpty()) {
                messages.add(UserMessage.builder()
                        .text(historyMessage)
                        .media(mediaList)
                        .build());
            } else {
                messages.add(new UserMessage(historyMessage));
            }
        }
    }

    private List<String> getFreshImageUrls(ShiroUserMessage shiroMsg, Bot bot) {
        if (!wineFoxBotChatProperties.getEnableImageAnalysis()) {
            return Collections.emptyList();
        }

        try {
            log.debug("Re-fetching message {} to get fresh image URLs.", shiroMsg.getId());
            ActionData<MsgResp> msgResp = bot.getMsg(shiroMsg.getMessageId().intValue());
            MsgResp msgData = Optional.ofNullable(msgResp).map(ActionData::getData).orElse(null);
            if (msgData != null && msgData.getMessage() != null) {
                List<ArrayMsg> arrayMsgs = MessageConverser.stringToArray(msgData.getMessage());
                List<String> freshImageUrls = ShiroUtils.getMsgImgUrlList(arrayMsgs);
                if (!freshImageUrls.isEmpty()) {
                    log.debug("Successfully re-fetched {} fresh image URLs for message {}.", freshImageUrls.size(), shiroMsg.getId());
                }
                return freshImageUrls;
            } else {
                log.warn("Could not re-fetch message data for message ID: {}. No image URLs will be used.", shiroMsg.getMessageId());
            }
        } catch (Exception e) {
            log.error("Failed to re-fetch image URLs for historical message {}", shiroMsg.getMessageId(), e);
        }
        return Collections.emptyList();
    }

    public static String cleanResponse(String rawResponse) {
        if (rawResponse == null) return "";

        String result = rawResponse.trim();

        // 1. 去除首尾的引号 (如果模型输出了 JSON String 格式)
        if (result.startsWith("\"") && result.endsWith("\"")) {
            try {
                // 如果不想引入库，简单的处理如下：
                result = result.substring(1, result.length() - 1);
            } catch (Exception e) {
                // 解析失败则保留原样
            }
        }

        // 2. 处理转义换行符 (Java 15+ text blocks 风格处理)
        result = result.replace("\\n", "\n");

        // 3. 处理转义的引号 \" -> "
        result = result.replace("\\\"", "\"");

        return result;
    }


    /**
     * 将 URL 字符串列表转换为 Spring AI 的 Media 对象列表。
     * <p>
     * 此实现现在使用 {@link UrlResource} 将图片 URL 直接传递给 AI。
     * 对于支持此功能的模型（如 GPT-4-Vision），这比先下载图片效率高得多，
     * 因为它避免了在我们的服务中进行网络 I/O 和数据处理。
     *
     * @param imageUrls 图片的 URL 列表
     * @return Spring AI 的 {@link Media} 对象列表
     */
    private List<Media> convertUrlsToMedia(List<String> imageUrls) {
        if (imageUrls == null || imageUrls.isEmpty()) {
            return Collections.emptyList();
        }

        return imageUrls.stream().map(url -> {
            try {
                return Optional.of(new Media(MimeTypeUtils.IMAGE_JPEG, new UrlResource(url)));
            } catch (MalformedURLException e) {
                log.warn("Invalid image URL found, cannot create UrlResource: {}. Skipping.", url);
                return Optional.<Media>empty();
            }
        }).filter(Optional::isPresent).map(Optional::get).collect(Collectors.toList());
    }
}