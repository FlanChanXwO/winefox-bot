package com.github.winefoxbot.service.ai.impl;

import cn.hutool.json.JSONUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.winefoxbot.config.WineFoxBotConfig;
import com.github.winefoxbot.model.entity.ShiroMessage;
import com.github.winefoxbot.service.ai.DeepSeekService;
import com.github.winefoxbot.service.shiro.ShiroMessagesService;
import com.github.winefoxbot.utils.BotUtils;
import com.mikuac.shiro.core.Bot;
import com.mikuac.shiro.dto.event.notice.PokeNoticeEvent;
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
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
@RequiredArgsConstructor
@ConditionalOnBean(name = "deepSeekChatClient")
public class DeepSeekiServiceImpl implements DeepSeekService {

    @Resource(name = "deepSeekChatClient")
    private ChatClient chatClient;
    private final WineFoxBotConfig wineFoxBotConfig;
    private final ShiroMessagesService shiroMessagesService;
    private final ObjectMapper objectMapper;

    private static final int CONTEXT_READ_LIMIT = 200;

    /**
     * The single entry point for all AI chat completions.
     * It fetches context, calls the AI, and returns the response.
     */
    @Override
    public String complete(Long sessionId, String sessionType, ObjectNode userMsg) {
        List<Message> messages = new ArrayList<>();
        messages.add(new SystemMessage(wineFoxBotConfig.getSystemPrompt()));

        List<ShiroMessage> history = shiroMessagesService.findLatestMessagesForContext(sessionId, sessionType, CONTEXT_READ_LIMIT);
        for (int i = history.size() - 1; i >= 0; i--) {
            ShiroMessage shiroMsg = history.get(i);
            try {
                String filteredText = BotUtils.getFilteredTextMessage(JSONUtil.toJsonStr(shiroMsg.getMessage()));

                ObjectNode messageForAI = objectMapper.createObjectNode();

                boolean isBotMessage = "message_sent".equals(shiroMsg.getDirection());
                String senderRole = isBotMessage ? "bot" : "user";

                messageForAI.put("sender", senderRole);
                messageForAI.put("uid", shiroMsg.getUserId());
                messageForAI.put("nickname", isBotMessage ? "酒狐" : shiroMsg.getUserId().toString());
                boolean isMaster = wineFoxBotConfig.getMaster().equals(Long.valueOf(shiroMsg.getUserId()));
                messageForAI.put("isMaster", isMaster);
                messageForAI.put("message", filteredText);

                String finalJsonForAI = objectMapper.writeValueAsString(messageForAI);

                if (isBotMessage) {
                    messages.add(new AssistantMessage(finalJsonForAI));
                } else {
                    messages.add(new UserMessage(finalJsonForAI));
                }

            } catch (IOException | NumberFormatException e) {
                log.error("Failed to process history message for AI context: {}", shiroMsg.getMessage(), e);
            }
        }

        if (userMsg != null) {
            try {
                String currentUserMessageJson = objectMapper.writeValueAsString(userMsg);
                messages.add(new UserMessage(currentUserMessageJson));
            } catch (JsonProcessingException e) {
                log.error("Failed to serialize current user message for AI context: {}", userMsg.toString(), e);
            }
        }

        log.info("Sending {} messages to AI for context.", messages.size());

        return chatClient.prompt(new Prompt(messages)).call().content();
    }


    @Override
    public void handlePokeMessage(Bot bot, PokeNoticeEvent e, boolean isGroup) {
        long botId = bot.getSelfId();
        if (!e.getTargetId().equals(botId)) { // Must be a poke to the bot
            return;
        }

        long userId = e.getUserId();
        long groupId = isGroup ? e.getGroupId() : 0L;
        Long sessionId = isGroup ? groupId : userId;
        String sessionType = isGroup ? "group" : "private";

        String nickname = isGroup ? BotUtils.getGroupMemberNickname(bot, groupId, userId, false) : BotUtils.getUserNickname(bot, userId);
        boolean shouldPokeBack = Math.random() < 0.5; // Simplified 50% chance


        // Create a JSON object representing the "poke" event for the AI.
        ObjectNode pokeJson = objectMapper.createObjectNode();
        pokeJson.put("sender", "user");
        pokeJson.put("uid", String.valueOf(userId));
        pokeJson.put("nickname", nickname);
        pokeJson.put("message", shouldPokeBack ? "(酒狐被戳了，并决定反击！)" : "(戳了一下酒狐)");
        pokeJson.put("isMaster", wineFoxBotConfig.getMaster().equals(userId));
        pokeJson.put("voice", false);

        try {
            // 回击
            if (shouldPokeBack) {
                // 1 到 2 秒的随机延迟
                TimeUnit.SECONDS.sleep((long) (0.5 + Math.random()));
                if (isGroup) {
                    bot.sendGroupPoke(groupId, userId);
                } else {
                    bot.sendFriendPoke(userId);
                }
            }
            String aiReply = this.complete(sessionId, sessionType, pokeJson);
            if (aiReply != null && !aiReply.isEmpty()) {
                if (isGroup) {
                    bot.sendGroupMsg(groupId, aiReply, false);
                } else {
                    bot.sendPrivateMsg(userId, aiReply, false);
                }
            }
        } catch (Exception ex) {
            log.error("Error handling poke message AI response", ex);
        }
    }

    // groupChat and privateChat methods are no longer needed.
}