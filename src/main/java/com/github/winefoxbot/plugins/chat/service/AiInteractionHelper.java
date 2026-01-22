package com.github.winefoxbot.plugins.chat.service;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.winefoxbot.core.model.entity.ShiroUserMessage;
import com.github.winefoxbot.core.model.enums.common.MessageDirection;
import com.github.winefoxbot.core.model.enums.common.MessageType;
import com.github.winefoxbot.core.utils.BotUtils;
import com.mikuac.shiro.core.Bot;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AiInteractionHelper {

    private final ObjectMapper objectMapper;
    // 格式化时间
    private final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * 用于传输解析后的消息结果（包含构建好的JSON Prompt和提取的图片URL）
     */
    @Data
    public static class AiMessageInput {
        private ObjectNode contentNode; // 发送给AI的文本部分（JSON结构）
        private List<String> imageUrls; // 该消息包含的图片URL列表

        public AiMessageInput(ObjectNode contentNode, List<String> imageUrls) {
            this.contentNode = contentNode;
            this.imageUrls = imageUrls;
        }
    }

    /**
     * 解析 OneBot 消息链，提取文本和图片
     * @param messageChain 消息链 JSON 数组
     * @param plainTextBuffer 用于回传纯文本内容的 StringBuilder
     * @param imageUrls 用于回传图片 URL 的 List
     */
    private void parseMessageChain(JSONArray messageChain, StringBuilder plainTextBuffer, List<String> imageUrls) {
        if (messageChain == null) return;

        for (Object item : messageChain) {
            if (item instanceof JSONObject segment) {
                String type = segment.getStr("type");
                JSONObject data = segment.getJSONObject("data");

                if ("text".equals(type)) {
                    plainTextBuffer.append(data.getStr("text"));
                } else if ("image".equals(type)) {
                    // 在文本中保留占位符，保持语境连贯
                    plainTextBuffer.append(" [图片] ");
                    // 提取 URL
                    String url = data.getStr("url");
                    if (url != null && !url.isEmpty()) {
                        imageUrls.add(url);
                    }
                } else if ("at".equals(type)) {
                    // 处理 At，视情况添加
                    // plainTextBuffer.append(" @").append(data.getStr("qq")).append(" ");
                } else if ("face".equals(type)) {
                    plainTextBuffer.append("[表情]");
                }
                // 其他类型可按需扩展
            }
        }
    }

    /**
     * 为从数据库加载的历史消息创建AI输入对象（支持多模态）。
     * @param shiroMsg 历史消息实体
     * @return AiMessageInput 包含JSON节点和图片列表
     */
    public AiMessageInput createHistoryMessageInput(ShiroUserMessage shiroMsg) {
        boolean isBotMessage = MessageDirection.MESSAGE_SENT.equals(shiroMsg.getDirection());
        String nickname = isBotMessage
                ? "酒狐"
                : (shiroMsg.getCard() != null ? shiroMsg.getCard() : shiroMsg.getNickname());

        ObjectNode messageNode = createBaseUserNode(shiroMsg.getUserId(), nickname);

        // 解析消息内容
        StringBuilder textBuffer = new StringBuilder();
        List<String> imageUrls = new ArrayList<>();

        // 只有用户发送的消息我们才提取图片传给AI，Bot发送的历史图片通常AI不需要再看一遍（或者很难看）
        // 这里假设只解析用户的图片，Bot的图片只保留文本占位符
        parseMessageChain(shiroMsg.getMessage(), textBuffer, isBotMessage ? new ArrayList<>() : imageUrls);

        // 覆盖/添加特定于历史记录的字段
        messageNode.put("sender", isBotMessage ? "bot" : "user");
        messageNode.put("time", formatter.format(shiroMsg.getTime()));
        messageNode.put("message", textBuffer.toString());
        messageNode.put("session_id", shiroMsg.getSessionId());
        messageNode.put("message_type",shiroMsg.getMessageType().getValue());

        return new AiMessageInput(messageNode, imageUrls);
    }

    /**
     * 为当前常规聊天消息创建AI输入对象（支持多模态）。
     * @param bot Bot实例
     * @param userId 用户ID
     * @param groupId 群ID (私聊时为null)
     * @param messageChain 原始消息链
     * @return AiMessageInput
     */
    public AiMessageInput createChatMessageInput(Bot bot, long userId, Long groupId, JSONArray messageChain) {
        boolean isGroupMessage = groupId != null;
        String nickname = isGroupMessage
                ? BotUtils.getGroupMemberNickname(bot, groupId, userId)
                : BotUtils.getUserNickname(bot, userId);

        StringBuilder textBuffer = new StringBuilder();
        List<String> imageUrls = new ArrayList<>();

        parseMessageChain(messageChain, textBuffer, imageUrls);

        ObjectNode node = createBaseUserNode(userId, nickname);
        node.put("message", textBuffer.toString());
        node.put("session_id", isGroupMessage ? groupId : userId);
        node.put("message_type", isGroupMessage ? MessageType.GROUP.getValue() : MessageType.PRIVATE.getValue());
        return new AiMessageInput(node, imageUrls);
    }

    /**
     * 为戳一戳事件创建AI输入JSON对象。
     * 戳一戳通常没有图片，维持原样但封装进 DTO
     */
    public AiMessageInput createPokeMessageInput(Bot bot, long userId, Long groupId, boolean isPokingBack) {
        String nickname = (groupId != null)
                ? BotUtils.getGroupMemberNickname(bot, groupId, userId)
                : BotUtils.getUserNickname(bot, userId);

        String message = isPokingBack ? "(酒狐被戳了，并决定反击！)" : "(戳了一下酒狐)";

        ObjectNode node = createBaseUserNode(userId, nickname);
        node.put("message", message);

        return new AiMessageInput(node, new ArrayList<>());
    }

    private ObjectNode createBaseUserNode(long userId, String nickname) {
        ObjectNode userNode = objectMapper.createObjectNode();
        userNode.put("sender", "user");
        userNode.put("uid", userId);
        userNode.put("nickname", nickname);
        return userNode;
    }
}