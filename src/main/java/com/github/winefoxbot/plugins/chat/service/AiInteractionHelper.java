package com.github.winefoxbot.plugins.chat.service;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import com.github.winefoxbot.core.context.BotContext;
import com.github.winefoxbot.core.model.entity.ShiroUserMessage;
import com.github.winefoxbot.core.model.enums.common.MessageDirection;
import com.github.winefoxbot.core.util.BotUtil;
import com.github.winefoxbot.core.util.MessageConverter;
import com.mikuac.shiro.common.utils.ShiroUtils;
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

    @Data
    public static class AiMessageInput {
        private String textContent; // 格式化后的文本
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
        JSONArray message = shiroMsg.getMessage();
        if (message != null) {
            for (int i = 0; i < message.size(); i++) {
                JSONObject item = null;
                try {
                    item = message.getJSONObject(i);
                } catch (Exception e) {
                    continue;
                }
                if (item != null && "image".equals(item.getStr("type"))) {
                    JSONObject data = item.getJSONObject("data");
                    if (data != null) {
                        String url = data.getStr("url");
                        if (StringUtils.isNotBlank(url)) {
                            imageUrls.add(url);
                        }
                    }
                }
            }
        }

        String rawText = shiroMsg.getPlainText();
        if (StringUtils.isBlank(rawText)) {
            rawText = !imageUrls.isEmpty() ? "[图片]" : "[无内容]";
        }

        String finalContent;
        if (isBotMessage) {
            // 当酒狐自身发送的消息包含图片时，需要按照 Prompt 的要求进行特殊处理
            if (!imageUrls.isEmpty()) {
                String systemText = "[System: 这是上一条酒狐发送的图片，请知悉]";
                // 如果原始消息只有图片（rawText被设置为"[图片]"），则直接使用系统文本
                if ("[图片]".equals(rawText)) {
                    finalContent = systemText;
                } else {
                    // 如果原始消息有文本，则将系统文本附加到后面，以符合Prompt“紧跟在你文字回复之后”的描述
                    finalContent = rawText + "\n" + systemText;
                }
            } else {
                finalContent = rawText;
            }
        } else {
            // 用户的历史记录，按照 Prompt 要求格式化
            finalContent = String.format("[%s(%s)]: %s", nickname, shiroMsg.getUserId(), rawText);
        }

        return new AiMessageInput(finalContent, imageUrls);
    }
    /**
     * 构建当前对话输入
     * <p>修复：支持图片提取 + 自动处理At唤醒词逻辑</p>
     */
    public AiMessageInput createChatMessageInput(AnyMessageEvent event) {
        Bot bot = BotContext.CURRENT_BOT.get();
        Long userId = event.getUserId();
        Long groupId = event.getGroupId();
        boolean isGroupMessage = groupId != null;

        // 获取昵称
        String nickname = isGroupMessage
                ? BotUtil.getGroupMemberNickname(bot, groupId, userId)
                : BotUtil.getUserNickname(bot, userId);

        // 1. 获取内容
        String rawMessage = event.getRawMessage();
        String plainText = MessageConverter.getPlainTextMessage(rawMessage);

        // 2. 提取图片列表
        List<String> imageUrls = ShiroUtils.getMsgImgUrlList(event.getArrayMsg());

        // 3. 处理唤醒词逻辑 (解决需要手动输入"酒狐"的问题)
        // 既然进入了这个方法，说明 Controller 层已经校验过 At 了 (或者在私聊)
        // 为了满足 Prompt 中 "提及名字才回复" 的设定，我们在此处做 Prompt Engineering
        String finalContent = plainText;

        // 只有当文本不包含"酒狐"时，才自动添加称呼前缀
        boolean hasWakeWord = StringUtils.containsAny(plainText, "酒狐");

        if (!hasWakeWord) {
            // 隐式添加唤醒词，让 AI 知道这是对它说的话
            // 格式示例: [张三(12345)]: 酒狐，(原始消息)
            finalContent = "酒狐，" + plainText;
        }

        // 构造符合 Prompt 要求的格式
        // 最终格式: [昵称(UID)]: (酒狐，)内容
        String formattedMsg = String.format("[%s(%s)]: %s", nickname, userId, finalContent);

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
                ? BotUtil.getGroupMemberNickname(bot, groupId, userId)
                : BotUtil.getUserNickname(bot, userId);

        String action = isPokingBack ? "(酒狐被戳了，并决定反击！)" : "(戳了一下酒狐)";
        String formattedMsg = String.format("[%s(%s)] - %s", nickname,userId, action);
        return new AiMessageInput(formattedMsg, new ArrayList<>());
    }
}
