package com.github.winefoxbot.plugins.chat.service;

import com.github.winefoxbot.core.context.BotContext;
import com.github.winefoxbot.core.model.entity.ShiroUserMessage;
import com.github.winefoxbot.core.model.enums.common.MessageDirection;
import com.github.winefoxbot.core.utils.BotUtils;
import com.mikuac.shiro.core.Bot;
import com.mikuac.shiro.dto.event.message.AnyMessageEvent;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * @author FlanChan
 */
@Service
@RequiredArgsConstructor
public class AiInteractionHelper {

    /**
     * 重构后的 DTO：不再包含 JSON Node，而是直接包含格式化好的文本
     */
    @Data
    public static class AiMessageInput {
        private String textContent; // 格式化后的文本，例如 "[张三]: 你好"
        private List<String> imageUrls; // 图片URL列表

        public AiMessageInput(String textContent, List<String> imageUrls) {
            this.textContent = textContent;
            this.imageUrls = imageUrls;
        }
    }

    /**
     * 构建历史消息输入
     * Bot 的消息直接返回内容，用户的消息格式化为 `[昵称]: 内容`
     */
    public AiMessageInput createHistoryMessageInput(ShiroUserMessage shiroMsg) {
        boolean isBotMessage = MessageDirection.MESSAGE_SENT.equals(shiroMsg.getDirection());
        String nickname = isBotMessage
                ? "酒狐"
                : (shiroMsg.getCard() != null ? shiroMsg.getCard() : shiroMsg.getNickname());

        List<String> imageUrls = new ArrayList<>();

        String finalContent;
        if (isBotMessage) {
            finalContent = shiroMsg.getPlainText();
        } else {
            // 用户的历史记录，按照 Prompt 要求格式化
            finalContent = String.format("[%s(%s)]: %s", nickname,shiroMsg.getUserId(), shiroMsg.getPlainText());
        }

        return new AiMessageInput(finalContent, imageUrls);
    }

    /**
     * 构建当前对话输入
     * 格式化为 `[昵称]: 内容`
     */
    public AiMessageInput createChatMessageInput(String message) {
        Bot bot = BotContext.CURRENT_BOT.get();
        AnyMessageEvent event = (AnyMessageEvent) BotContext.CURRENT_MESSAGE_EVENT.get();
        Long userId = event.getUserId();
        Long groupId = event.getGroupId();
        boolean isGroupMessage = groupId != null;
        String nickname = isGroupMessage
                ? BotUtils.getGroupMemberNickname(bot, groupId, userId)
                : BotUtils.getUserNickname(bot, userId);

        List<String> imageUrls = new ArrayList<>();

        // 构造符合 Prompt 要求的格式
        String formattedMsg = String.format("[%s(%s)]: %s%s", nickname,userId, message.contains("酒狐") ? "酒狐，" : StringUtils.EMPTY, message);

        return new AiMessageInput(formattedMsg, imageUrls);
    }

    /**
     * 构建戳一戳输入
     */
    public AiMessageInput createPokeMessageInput(boolean isPokingBack) {
        Bot bot = BotContext.CURRENT_BOT.get();
        AnyMessageEvent event = (AnyMessageEvent) BotContext.CURRENT_MESSAGE_EVENT.get();
        Long userId = event.getUserId();
        Long groupId = event.getGroupId();

        String nickname = (groupId != null)
                ? BotUtils.getGroupMemberNickname(bot, groupId, userId)
                : BotUtils.getUserNickname(bot, userId);

        String action = isPokingBack ? "(酒狐被戳了，并决定反击！)" : "(戳了一下酒狐)";
        String formattedMsg = String.format("[%s(%s)] - %s", nickname,userId, action);
        return new AiMessageInput(formattedMsg, new ArrayList<>());
    }
}
