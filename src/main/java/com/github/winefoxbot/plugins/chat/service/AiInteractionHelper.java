package com.github.winefoxbot.plugins.chat.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.winefoxbot.core.model.entity.ShiroUserMessage;
import com.github.winefoxbot.core.utils.BotUtils;
import com.mikuac.shiro.core.Bot;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;

@Service
@RequiredArgsConstructor
public class AiInteractionHelper {

    private final ObjectMapper objectMapper;
    // 格式化时间
    private final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * 为从数据库加载的历史消息创建AI输入JSON对象。
     * @param shiroMsg 历史消息实体
     * @param filteredText 过滤后的消息文本
     * @return 构建好的 ObjectNode
     */
    public ObjectNode createHistoryMessageNode(ShiroUserMessage shiroMsg, String filteredText) {
        boolean isBotMessage = "message_sent".equals(shiroMsg.getDirection());
        String nickname = isBotMessage
                ? "酒狐"
                : (shiroMsg.getCard() != null ? shiroMsg.getCard() : shiroMsg.getNickname());

        ObjectNode messageNode = createBaseUserNode(shiroMsg.getUserId(), nickname);

        // 覆盖/添加特定于历史记录的字段
        messageNode.put("sender", isBotMessage ? "bot" : "user");
        messageNode.put("time", formatter.format(shiroMsg.getTime()));
        messageNode.put("message", filteredText);

        return messageNode;
    }

    /**
     * 为常规聊天消息创建AI输入JSON对象。
     * @param bot Bot实例
     * @param userId 用户ID
     * @param groupId 群ID (私聊时为null)
     * @param plainMessage 纯文本消息
     * @return 构建好的 ObjectNode
     */
    public ObjectNode createChatMessageNode(Bot bot, long userId, Long groupId, String plainMessage) {
        String nickname = (groupId != null)
                ? BotUtils.getGroupMemberNickname(bot, groupId, userId)
                : BotUtils.getUserNickname(bot, userId);

        return createBaseUserNode(userId, nickname)
                .put("message", plainMessage);
    }

    /**
     * 为戳一戳事件创建AI输入JSON对象。
     * @param bot Bot实例
     * @param userId 用户ID
     * @param groupId 群ID (私聊时为null)
     * @param isPokingBack 是否是反戳
     * @return 构建好的 ObjectNode
     */
    public ObjectNode createPokeMessageNode(Bot bot, long userId, Long groupId, boolean isPokingBack) {
        String nickname = (groupId != null)
                ? BotUtils.getGroupMemberNickname(bot, groupId, userId)
                : BotUtils.getUserNickname(bot, userId);

        String message = isPokingBack ? "(酒狐被戳了，并决定反击！)" : "(戳了一下酒狐)";

        return createBaseUserNode(userId, nickname)
                .put("message", message);
    }

    /**
     * 创建包含用户基础信息的 ObjectNode。
     * @param userId 用户ID
     * @param nickname 用户昵称
     * @return 包含 sender, uid, nickname 的 ObjectNode
     */
    private ObjectNode createBaseUserNode(long userId, String nickname) {
        ObjectNode userNode = objectMapper.createObjectNode();
        userNode.put("sender", "user");
        userNode.put("uid", String.valueOf(userId));
        userNode.put("nickname", nickname);
        return userNode;
    }
}
