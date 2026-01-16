package com.github.winefoxbot.plugins.chat;

import com.github.winefoxbot.core.annotation.Block;
import com.github.winefoxbot.core.annotation.Limit;
import com.github.winefoxbot.core.annotation.Plugin;
import com.github.winefoxbot.core.annotation.PluginFunction;
import com.github.winefoxbot.core.model.enums.MessageType;
import com.github.winefoxbot.core.model.enums.Permission;
import com.github.winefoxbot.core.service.reply.VoiceReplyService;
import com.github.winefoxbot.core.service.shiro.ShiroMessagesService;
import com.github.winefoxbot.core.service.shiro.ShiroSessionStateService;
import com.github.winefoxbot.core.utils.BotUtils;
import com.github.winefoxbot.core.utils.MessageConverter;
import com.github.winefoxbot.plugins.chat.service.AiInteractionHelper;
import com.github.winefoxbot.plugins.chat.service.AiInteractionHelper.AiMessageInput;
import com.github.winefoxbot.plugins.chat.service.OpenAiService;
import com.mikuac.shiro.annotation.*;
import com.mikuac.shiro.annotation.common.Order;
import com.mikuac.shiro.annotation.common.Shiro;
import com.mikuac.shiro.common.utils.MsgUtils;
import com.mikuac.shiro.common.utils.ShiroUtils;
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

import static com.github.winefoxbot.core.config.app.WineFoxBotConfig.COMMAND_PREFIX;
import static com.github.winefoxbot.core.config.app.WineFoxBotConfig.COMMAND_SUFFIX;
import static com.mikuac.shiro.core.BotPlugin.MESSAGE_IGNORE;

@Plugin(
        name = "娱乐功能",
        permission = Permission.USER,
        order = 6)
@Shiro
@ConditionalOnClass(OpenAiService.class)
@Component
@Slf4j
@RequiredArgsConstructor
public class ChatPlugin {

    // 核心服务
    private final OpenAiService openAiService;
    private final ShiroMessagesService shiroMessagesService;
    private final VoiceReplyService voiceReplyService;
    // 辅助类，用于构建AI输入
    private final AiInteractionHelper aiInteractionHelper;
    private final ShiroSessionStateService shiroSessionStateService;

    // ... (戳一戳相关的字段保持不变)
    private final Map<Long, Integer> pokePityCounter = new ConcurrentHashMap<>();
    private static final int PITY_THRESHOLD = 30;
    private static final double PROACTIVE_POKE_BACK_CHANCE = 0.3;
    private static final double VOICE_REPLY_CHANCE = 0.2;


    @PluginFunction(
            name = "聊天回复",
            description = "艾特酒狐或者直接在私聊中给酒狐发消息也许会有回应哦，戳一戳也有。",
            permission = Permission.USER,
            autoGenerateHelp = false
    )
    @AnyMessageHandler
    @MessageHandlerFilter(types = {MsgTypeEnum.unknown})
    public int chatDoc() {
        return MESSAGE_IGNORE;
    }

    @PluginFunction(name = "清空会话",
            description = "清空当前会话的消息记录，重新开始对话。",
            permission = Permission.ADMIN,
            hidden = true,
            commands = {COMMAND_PREFIX + "清空会话" + COMMAND_SUFFIX})
    @AnyMessageHandler
    @MessageHandlerFilter(types = MsgTypeEnum.text, cmd = "^/清空会话$")
    public void clearConversation(Bot bot, AnyMessageEvent event) {
        Long sessionId = BotUtils.getSessionId(event);
        MessageType messageType = MessageType.fromValue(event.getMessageType());
        shiroMessagesService.clearConversation(sessionId, messageType);
        bot.sendMsg(event, "当前会话的消息记录已经被我忘掉啦，可以开始新的聊天咯！", false);
    }

    @Async
    @Order(100)
    @Block
    @PrivateMessageHandler
    @PluginFunction(name = "私聊聊天回复",
            hidden = true,
            description = "在私聊中与酒狐进行智能聊天。",
            permission = Permission.SUPERADMIN)
    @MessageHandlerFilter(types = {MsgTypeEnum.text, MsgTypeEnum.image})
    public void handlePrivateChatMessage(Bot bot, PrivateMessageEvent event) {
        String sessionKey = shiroSessionStateService.getSessionKey(event);
        if (shiroSessionStateService.isInCommandMode(sessionKey)) {
            return;
        }

        // 注意：这里不再单纯检查 plainMessage 是否为空，因为消息可能只包含图片
        // 但是通常需要过滤掉纯命令
        String plainMessage = MessageConverter.getPlainTextMessage(event.getMessage());
        if (plainMessage.startsWith("/")) {
            return;
        }

        Long userId = event.getUserId();

        // 关键修改：传递原始的 JSONArray (event.getMessage())
        AiMessageInput userMsg = aiInteractionHelper.createChatMessageInput(bot, userId, null, MessageConverter.parseCQToJSONArray(event.getRawMessage()));
        String resp = openAiService.complete(userId, MessageType.PRIVATE, userMsg);
        if (resp != null && !resp.isEmpty()) {
            bot.sendPrivateMsg(userId, resp, false);
        }
    }


    @GroupMessageHandler
    @Async
    @Order(100)
    @Block
    @Limit(userPermits = 1, timeInSeconds = 5, notificationIntervalSeconds = 30, message = "说话太快了，酒狐需要思考一会儿哦~")
    @MessageHandlerFilter(types = {MsgTypeEnum.text, MsgTypeEnum.image},at = AtEnum.NEED)
    public void handleGroupChatMessage(Bot bot, GroupMessageEvent event) {
        String plainMessage = MessageConverter.getPlainTextMessage(event.getMessage());
        if (plainMessage.startsWith("/")) {
            return;
        }

        Long groupId = event.getGroupId();
        Long userId = event.getUserId();

        // 关键修改：传递原始的 JSONArray
        AiMessageInput userMsg = aiInteractionHelper.createChatMessageInput(bot, userId, groupId, MessageConverter.parseCQToJSONArray(event.getRawMessage()));
        String resp = openAiService.complete(groupId, MessageType.GROUP, userMsg);

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
     */
    private boolean decideAndExecutePokeAction(Bot bot, PokeNoticeEvent event, boolean isGroup, boolean pityTriggered) {
        long userId = event.getUserId();
        Long groupId = isGroup ? event.getGroupId() : -1L;

        // 1. ��立决策：是否要“反戳”
        boolean shouldPokeBack = ThreadLocalRandom.current().nextDouble() < PROACTIVE_POKE_BACK_CHANCE;

        // 2. 独立决策：是否要“播放语音” (保底触发 或 随机命中)
        // 修复问题：反戳不再 100% 触发语音，被动戳一戳的语音概率也统一提高
        boolean shouldPlayVoice = pityTriggered || (ThreadLocalRandom.current().nextDouble() < VOICE_REPLY_CHANCE);

        if (pityTriggered && shouldPlayVoice) {
            log.info("用户 {} 触发戳一戳语音保底 (当前计数: {}/{})", userId, pokePityCounter.getOrDefault(userId, 0), PITY_THRESHOLD);
        }

        // 分支 A: 执行反戳逻辑
        if (shouldPokeBack) {
            pokeBack(bot, isGroup, groupId, userId); // 执行反戳

            if (shouldPlayVoice) {
                // 尝试获取主动反击的语音
                Optional<File> voiceFile = voiceReplyService.drawVoice("poke_proactive");
                if (voiceFile.isPresent()) {
                    sendPokeVoice(bot, isGroup, groupId, userId, voiceFile.get().getAbsolutePath());
                    return true; // 播放了语音
                }
            }

            // 如果没决定播放语音，或者语音文件没找到，回退到 AI 文本
            handlePokeWithAI(bot, event, isGroup, true);
            return false;
        }

        // 分支 B: 被动接受逻辑 (无反戳)
        if (shouldPlayVoice) {
            // 尝试获取被动接受的语音
            Optional<File> voiceFile = voiceReplyService.drawVoice("poke_passive");
            if (voiceFile.isPresent()) {
                sendPokeVoice(bot, isGroup, groupId, userId, voiceFile.get().getAbsolutePath());
                return true; // 播放了语音
            }
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
        MessageType messageType = isGroup ? MessageType.GROUP : MessageType.PRIVATE;

        AiMessageInput pokeInput = aiInteractionHelper.createPokeMessageInput(bot, userId, isGroup ? groupId : null, isPokingBack);
        String aiReply = openAiService.complete(sessionId, messageType, pokeInput);

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