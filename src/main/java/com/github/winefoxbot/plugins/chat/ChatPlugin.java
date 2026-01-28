package com.github.winefoxbot.plugins.chat;

import com.github.winefoxbot.core.annotation.common.Block;
import com.github.winefoxbot.core.annotation.common.Limit;
import com.github.winefoxbot.core.annotation.plugin.Plugin;
import com.github.winefoxbot.core.annotation.plugin.PluginFunction;
import com.github.winefoxbot.core.model.enums.common.MessageType;
import com.github.winefoxbot.core.model.enums.common.Permission;
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
import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.scheduling.annotation.Async;

import java.io.File;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

import static com.github.winefoxbot.core.config.app.WineFoxBotConfig.COMMAND_PREFIX;
import static com.github.winefoxbot.core.config.app.WineFoxBotConfig.COMMAND_SUFFIX;
import static com.mikuac.shiro.core.BotPlugin.MESSAGE_IGNORE;

/**
 * @author FlanChan
 */
@Plugin(
        name = "聊天功能",
        permission = Permission.USER,
        description = "提供群聊和私聊的智能聊天功能，支持戳一戳互动。",
        order = 6)
@ConditionalOnClass(OpenAiService.class)
@Slf4j
@RequiredArgsConstructor
public class ChatPlugin {

    private final OpenAiService openAiService;
    private final ShiroMessagesService shiroMessagesService;
    private final VoiceReplyService voiceReplyService;
    private final AiInteractionHelper aiInteractionHelper;

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

    @AnyMessageHandler
    @Async
    @Order(100)
    @Block
    @Limit(userPermits = 1, timeInSeconds = 5, notificationIntervalSeconds = 30, message = "说话太快了，酒狐需要思考一会儿哦~")
    @MessageHandlerFilter(types = {MsgTypeEnum.text, MsgTypeEnum.image},at = AtEnum.NEED, cmd = "^(?!/)(?!\\s+$).+")
    public void handleChatMessage(Bot bot, AnyMessageEvent event) {
        AiMessageInput userMsg = aiInteractionHelper.createChatMessageInput(event);
        String resp = openAiService.complete(userMsg);
        if (resp != null && !resp.isEmpty()) {
            MsgUtils msgBuilder = MsgUtils.builder().at(event.getUserId()).text(StringUtils.SPACE).text(resp);
            bot.sendMsg(event, msgBuilder.build(), false);
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
            handlePokeWithAi(bot, event, isGroup, true);
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
        handlePokeWithAi(bot, event, isGroup, false);
        return false; // 未播放语音
    }

    /**
     * 调用 AI 服务处理戳一戳事件
     */
    private void handlePokeWithAi(Bot bot, PokeNoticeEvent event, boolean isGroup, boolean isPokingBack) {
        Long userId = event.getUserId();
        Long groupId = event.getGroupId();
        AiMessageInput pokeInput = aiInteractionHelper.createPokeMessageInput(isPokingBack);
        String aiReply = openAiService.complete(pokeInput);

        if (aiReply != null && !aiReply.isEmpty()) {
            if (isGroup) {
                bot.sendGroupMsg(groupId, MsgUtils.builder().at(userId).text(aiReply).build(), false);
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
        if (isGroup) {
            bot.sendGroupMsg(groupId, MsgUtils.builder().at(userId).voice(voiceFilePath).build(), false);
        } else {
            bot.sendPrivateMsg(userId, MsgUtils.builder().voice(voiceFilePath).build(), false);
        }
    }
}