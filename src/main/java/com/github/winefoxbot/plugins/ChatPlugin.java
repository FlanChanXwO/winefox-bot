package com.github.winefoxbot.plugins;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.winefoxbot.annotation.Block;
import com.github.winefoxbot.annotation.Limit;
import com.github.winefoxbot.annotation.PluginFunction;
import com.github.winefoxbot.model.enums.Permission;
import com.github.winefoxbot.service.chat.AiInteractionHelper;
import com.github.winefoxbot.service.chat.DeepSeekService;
import com.github.winefoxbot.service.reply.VoiceReplyService;
import com.github.winefoxbot.service.shiro.ShiroMessagesService;
import com.github.winefoxbot.utils.BotUtils;
import com.mikuac.shiro.annotation.*;
import com.mikuac.shiro.annotation.common.Order;
import com.mikuac.shiro.annotation.common.Shiro;
import com.mikuac.shiro.common.utils.MsgUtils;
import com.mikuac.shiro.core.Bot;
import com.mikuac.shiro.dto.event.message.AnyMessageEvent;
import com.mikuac.shiro.dto.event.message.GroupMessageEvent;
import com.mikuac.shiro.dto.event.message.PrivateMessageEvent;
import com.mikuac.shiro.dto.event.notice.PokeNoticeEvent;
import com.mikuac.shiro.enums.AtEnum;
import com.mikuac.shiro.enums.MsgTypeEnum;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.io.File;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

@Shiro
@ConditionalOnClass(DeepSeekService.class)
@Component
@Slf4j
@RequiredArgsConstructor
public class ChatPlugin {

    // 核心服务
    private final DeepSeekService deepSeekService;
    private final ShiroMessagesService shiroMessagesService;
    private final VoiceReplyService voiceReplyService;
    // 辅助类，用于构建AI输入
    private final AiInteractionHelper aiInteractionHelper;

    /**
     * 用户戳一戳保底计数器
     */
    private final Map<Long, Integer> pokePityCounter = new ConcurrentHashMap<>();
    private static final int PITY_THRESHOLD = 30; // 保底阈值

    /**
     * 戳一戳后，机器人“主动反戳”的概率 (0.0 to 1.0)
     */
    private static final double PROACTIVE_POKE_BACK_CHANCE = 0.3; // 30%

    /**
     * 在“被动回应”模式下，尝试播放语音的概率 (0.0 to 1.0)
     */
    private static final double PASSIVE_VOICE_REPLY_CHANCE = 0.05; // 5%

    @PluginFunction(group = "聊天功能",
            name = "清空会话",
            description = "清空当前会话的消息记录，重新开始对话。",
            permission = Permission.ADMIN,
            commands = {"/清空会话"})
    @AnyMessageHandler
    @MessageHandlerFilter(types = MsgTypeEnum.text, cmd = "^/清空会话$")
    public void clearConversation(Bot bot, AnyMessageEvent event) {
        Long groupId = event.getGroupId();
        Long sessionId = (groupId != null) ? groupId : event.getUserId();
        String sessionType = (groupId != null) ? "group" : "private";

        shiroMessagesService.clearConversation(sessionId, sessionType);
        bot.sendMsg(event, "当前会话的消息记录已经被酒狐忘掉啦，可以开始新的聊天咯！", false);
    }

    @PrivateMessageHandler
    @Async
    @Order(100)
    @Block
    public void handlePrivateChatMessage(Bot bot, PrivateMessageEvent event) {
        String plainMessage = BotUtils.getPlainTextMessage(event.getMessage());
        if (plainMessage.isEmpty() || plainMessage.startsWith("/")) {
            return;
        }

        Long userId = event.getUserId();

        // 使用Helper构建userMsg
        ObjectNode userMsg = aiInteractionHelper.createChatMessageNode(bot, userId, null, plainMessage);
        String resp = deepSeekService.complete(userId, "private", userMsg);

        if (resp != null && !resp.isEmpty()) {
            bot.sendPrivateMsg(userId, resp, false);
        }
    }

    @PluginFunction(group = "聊天功能",
            name = "聊天回复",
            description = "当用户在群聊中At机器人发送消息时，进行智能回复。",
            permission = Permission.USER
    )
    @GroupMessageHandler
    @MessageHandlerFilter(at = AtEnum.NEED)
    @Async
    @Order(100)
    @Block
    @Limit(userPermits = 1, timeInSeconds = 10, notificationIntervalSeconds = 30, message = "说话太快了，酒狐需要思考一会儿哦~")
    public void handleGroupChatMessage(Bot bot, GroupMessageEvent event) {
        String plainMessage = BotUtils.getPlainTextMessage(event.getMessage());
        if (plainMessage.isEmpty() || plainMessage.startsWith("/")) {
            return;
        }

        Long groupId = event.getGroupId();
        Long userId = event.getUserId();

        // 使用Helper构建userMsg
        ObjectNode userMsg = aiInteractionHelper.createChatMessageNode(bot, userId, groupId, plainMessage);
        String resp = deepSeekService.complete(groupId, "group", userMsg);

        if (resp != null && !resp.isEmpty()) {
            MsgUtils msgBuilder = MsgUtils.builder().at(userId).text(" ").text(resp);
            bot.sendGroupMsg(groupId, msgBuilder.build(), false);
        }
    }

    @GroupPokeNoticeHandler
    @Limit(userPermits = 1, timeInSeconds = 10, notificationIntervalSeconds = 30, message = "戳得太快了，酒狐需要休息一下哦~")
    @Async
    public void handleGroupPokeNotice(Bot bot, PokeNoticeEvent event) {
        if (!event.getTargetId().equals(bot.getSelfId())) {
            return;
        }
        handlePokeWithPity(bot, event, true);
    }

    @PrivatePokeNoticeHandler
    @Limit(userPermits = 1, timeInSeconds = 1, notificationIntervalSeconds = 30, message = "戳得太快了，酒狐需要休息一下哦~")
    @Async
    public void handlePrivatePokeNotice(Bot bot, PokeNoticeEvent event) {
        if (!event.getTargetId().equals(bot.getSelfId())) {
            return;
        }
        handlePokeWithPity(bot, event, false);
    }

    /**
     * 处理带保底机制的戳一戳逻辑，包含完整的响应决策。
     */
    private void handlePokeWithPity(Bot bot, PokeNoticeEvent event, boolean isGroup) {
        long userId = event.getUserId();
        int currentPity = pokePityCounter.getOrDefault(userId, 0);
        boolean pityTriggered = currentPity >= PITY_THRESHOLD;

        // 决策并执行动作
        boolean voiceReplied = decideAndExecutePokeAction(bot, event, isGroup, pityTriggered);

        // 根据决策结果更新保底计数器
        if (voiceReplied) {
            if (currentPity > 0) {
                pokePityCounter.put(userId, 0);
                log.debug("用户 {} 成功触发语音回复，保底计数器已重置。", userId);
            }
        } else {
            int newPity = currentPity + 1;
            pokePityCounter.put(userId, newPity);
            log.debug("用户 {} 本次未触发语音回复，保底计数器增加: {} -> {}", userId, currentPity, newPity);
        }
    }

    /**
     * 决策并执行戳一戳的响应动作。
     * @return boolean 返回 true 表示播放了语音，false 表示没有。
     */
    private boolean decideAndExecutePokeAction(Bot bot, PokeNoticeEvent event, boolean isGroup, boolean pityTriggered) {
        long userId = event.getUserId();
        Long groupId = isGroup ? event.getGroupId() : -1L;

        // 1. 决定是否要“反戳”（Proactive）
        boolean shouldPokeBack = Math.random() < PROACTIVE_POKE_BACK_CHANCE;
        if (shouldPokeBack) {
            Optional<File> voiceFile = voiceReplyService.drawVoice("poke_proactive");
            pokeBack(bot, isGroup, groupId, userId); // 无论如何都先反戳
            if (voiceFile.isPresent()) {
                sendPokeVoice(bot, isGroup, groupId, userId, voiceFile.get().getAbsolutePath());
                return true; // 播放了语音
            } else {
                // 没有语音，回退到AI
                handlePokeWithAI(bot, event, isGroup, true);
                return false; // 未播放语音
            }
        }

        // 2. 如果不反戳，进入“被动”回复逻辑 (Passive)
        if (pityTriggered) {
            log.info("用户 {} 触发戳一戳语音保底 (当前计数: {}/{})", userId, pokePityCounter.getOrDefault(userId, 0), PITY_THRESHOLD);
        }

        // 2.1 保底触发或概率触发，尝试获取语音
        Optional<File> voiceFile = Optional.empty();
        if (pityTriggered || ThreadLocalRandom.current().nextDouble() < PASSIVE_VOICE_REPLY_CHANCE) {
            voiceFile = voiceReplyService.drawVoice("poke_passive");
        }

        // 2.2 如果获取到语音则播放
        if (voiceFile.isPresent()) {
            sendPokeVoice(bot, isGroup, groupId, userId, voiceFile.get().getAbsolutePath());
            return true; // 播放了语音
        }

        // 3. 所有语音路径都未命中，执行最终的AI文本回复
        handlePokeWithAI(bot, event, isGroup, false);
        return false; // 未播放语音
    }

    /**
     * 调用 AI 服务处理戳一戳事件
     */
    private void handlePokeWithAI(Bot bot, PokeNoticeEvent event, boolean isGroup, boolean isPokingBack) {
        long userId = event.getUserId();
        long groupId = isGroup ? event.getGroupId() : 0L;
        Long sessionId = isGroup ? groupId : userId;
        String sessionType = isGroup ? "group" : "private";

        // 使用Helper构建pokeJson
        ObjectNode pokeJson = aiInteractionHelper.createPokeMessageNode(bot, userId, isGroup ? groupId : null, isPokingBack);
        String aiReply = deepSeekService.complete(sessionId, sessionType, pokeJson);

        if (aiReply != null && !aiReply.isEmpty()) {
            if (isGroup) {
                bot.sendGroupMsg(groupId, aiReply, false);
            } else {
                bot.sendPrivateMsg(userId, aiReply, false);
            }
        }
    }


    private void pokeBack(Bot bot, boolean isGroup, long groupId, long userId) {
        try {
            // 随机延迟，让反戳更自然
            TimeUnit.MILLISECONDS.sleep(ThreadLocalRandom.current().nextLong(500, 1500));
            if (isGroup) {
                bot.sendGroupPoke(groupId, userId);
            } else {
                bot.sendFriendPoke(userId);
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            log.warn("Poking back was interrupted.", ex);
        }
    }

    private void sendPokeVoice(Bot bot, boolean isGroup, Long groupId, Long userId, String voiceFilePath) {
        MsgUtils msg = MsgUtils.builder().voice(voiceFilePath);
        if (isGroup) {
            bot.sendGroupMsg(groupId, msg.build(), false);
        } else {
            bot.sendPrivateMsg(userId, msg.build(), false);
        }
    }
}
