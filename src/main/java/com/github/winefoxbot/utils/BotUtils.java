package com.github.winefoxbot.utils;

import com.github.winefoxbot.model.dto.MessageSegment;
import com.github.winefoxbot.model.dto.shiro.GroupMemberInfo;
import com.github.winefoxbot.model.enums.GroupMemberRole;
import com.google.gson.*;
import com.mikuac.shiro.core.Bot;
import com.mikuac.shiro.dto.action.common.ActionData;
import com.mikuac.shiro.dto.action.response.GroupInfoResp;
import com.mikuac.shiro.dto.action.response.GroupMemberInfoResp;
import com.mikuac.shiro.dto.action.response.StrangerInfoResp;
import com.mikuac.shiro.dto.event.message.AnyMessageEvent;
import com.mikuac.shiro.dto.event.message.GroupMessageEvent;
import com.mikuac.shiro.dto.event.message.PrivateMessageEvent;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author FlanChan (badapple495@outlook.com)
 * @since 2025-12-08-21:02
 */
@Slf4j
public final class BotUtils {
    private BotUtils() {
    }

    /**
     * 获取群成员昵称
     *
     * @param bot
     * @param groupId
     * @param userId
     * @return
     */
    public static String getGroupMemberNickname(Bot bot, Long groupId, Long userId) {
        // 如果是陌生人，使用陌生人信息接口获取昵称
        if (groupId == null) {
            return getUserNickname(bot, userId);
        } else {
            // 否则使用群成员信息接口获取昵称
            ActionData<GroupMemberInfoResp> groupMemberInfo = bot.getGroupMemberInfo(groupId, userId, false);
            if (groupMemberInfo.getRetCode() == 0) {
                String card = groupMemberInfo.getData().getCard();
                return card.isBlank() ? groupMemberInfo.getData().getNickname() : card;
            } else {
                return getUserNickname(bot, userId);
            }
        }
    }


    /**
     * 获取陌生人昵称
     *
     * @param bot
     * @param userId
     * @return
     */
    public static String getStrangeNickname(Bot bot, Long userId) {
        return getUserNickname(bot,userId);
    }


    /**
     * 获取群成员昵称
     *
     * @param bot
     * @param groupId
     * @param userId
     * @return
     */
    public static GroupMemberInfo getGroupMemberInfo(Bot bot, Long groupId, Long userId) {
        GroupMemberInfo groupMember = new GroupMemberInfo();
        groupMember.setUserId(userId);
        groupMember.setGroupId(groupId);

        ActionData<GroupMemberInfoResp> groupMemberInfo = bot.getGroupMemberInfo(groupId, userId, false);
        if (groupMemberInfo.getRetCode() == 0) {
            GroupMemberInfoResp data = groupMemberInfo.getData();
            groupMember.setNickname(data.getNickname());
            groupMember.setCard(data.getCard());
            groupMember.setRole(GroupMemberRole.fromValue(data.getRole()));
        } else {
            ActionData<StrangerInfoResp> strangerInfo = bot.getStrangerInfo(userId, false);
            if (strangerInfo.getRetCode() == 0) {
                StrangerInfoResp data = strangerInfo.getData();
                groupMember.setNickname(data.getNickname());
                groupMember.setCard(data.getNickname());
            } else {
                groupMember.setNickname(userId.toString());
                groupMember.setCard(userId.toString());
            }
        }
        return groupMember;
    }


    public static String getPlainTextMessage(String message) {
        // 移除消息中的CQ码
        return message.replaceAll("\\[CQ:.*?\\]", "").trim();
    }

    public static String getGroupName(Bot bot, Long groupId) {
        ActionData<GroupInfoResp> resp = bot.getGroupInfo(groupId, true);
        return resp.getRetCode() == 0 ? resp.getData().getGroupName() : groupId.toString();
    }

    private static final Gson gson = new GsonBuilder()
            .disableHtmlEscaping().create();

    private static final Pattern cqCodePattern = Pattern.compile("\\[CQ:([a-zA-Z0-9_.-]+)((,.*?)*?)\\]");

    /**
     * 使用 Gson 将包含CQ码的字符串解析为JSON数组格式。
     *
     * @param message 输入的消息字符串.
     * @return 代表消息段的JSON数组字符串.
     */
    public static String parseCQtoJsonStr(String message) {
        List<MessageSegment<?>> segments = new ArrayList<>();
        Matcher matcher = cqCodePattern.matcher(message);
        int lastEnd = 0;

        while (matcher.find()) {
            // 1. 添加在CQ码之前的文本段
            if (matcher.start() > lastEnd) {
                String text = message.substring(lastEnd, matcher.start());
                segments.add(new MessageSegment<>("text", new MessageSegment.TextData(text)));
            }

            // 2. 处理并添加CQ码段
            String cqType = matcher.group(1);
            String paramsStr = matcher.group(2);

            MessageSegment.CQCodeData cqData = new MessageSegment.CQCodeData();
            if (paramsStr != null && !paramsStr.isEmpty()) {
                // 移除前导逗号并分割
                String[] params = paramsStr.substring(1).split(",");
                for (String param : params) {
                    String[] kv = param.split("=", 2);
                    if (kv.length == 2) {
                        if (kv[1].startsWith("base64")) {
                            cqData.put(kv[0], "base64");
                        } else {
                            cqData.put(kv[0], kv[1]);
                        }
                    }
                }
            }
            segments.add(new MessageSegment<>(cqType, cqData));

            lastEnd = matcher.end();
        }

        // 3. 添加最后一个CQ码之后的任何尾随文本
        if (lastEnd < message.length()) {
            String text = message.substring(lastEnd);
            segments.add(new MessageSegment<>("text", new MessageSegment.TextData(text)));
        }

        // 4. 使用Gson将Java对象列表转换为JSON字符串
        return gson.toJson(segments);
    }


    /**
     * 过滤消息内容，提取纯文本信息，忽略图片、表情等非文本内容。
     * @param jsonMessage
     * @return
     */
    public static String getFilteredTextMessage(String jsonMessage) {
        if (jsonMessage == null || jsonMessage.isEmpty()) {
            return "";
        }

        StringBuilder plainText = new StringBuilder();

        try {
            // Use Gson's JsonParser to parse the string into a JsonElement
            JsonElement rootElement = JsonParser.   parseString(jsonMessage);

            if (rootElement.isJsonArray()) {
                JsonArray messageArray = rootElement.getAsJsonArray();

                // Iterate over each segment in the message array
                for (JsonElement segmentElement : messageArray) {
                    if (!segmentElement.isJsonObject()) continue;
                    JsonObject segment = segmentElement.getAsJsonObject();

                    String type = segment.has("type") ? segment.get("type").getAsString() : "";
                    JsonObject data = segment.has("data") && segment.get("data").isJsonObject()
                            ? segment.get("data").getAsJsonObject()
                            : null;

                    switch (type) {
                        case "text":
                            if (data != null && data.has("text")) {
                                plainText.append(data.get("text").getAsString());
                            }
                            break;
                        case "at":
                            if (data != null && data.has("qq")) {
                                // Format @ mentions consistently for the AI
                                plainText.append("@").append(data.get("qq").getAsString()).append(" ");
                            }
                            break;
                        case "image":
                            // Represent image with a simple placeholder
                            plainText.append("[图片]");
                            break;
                        case "face":
                            // Represent face/emoticon with a placeholder
                            plainText.append("[表情]");
                            break;
                        default:
                            // Ignore other types of message segments by default
                            break;
                    }
                }
            } else {
                // Fallback for non-JSON-array inputs (e.g., plain text that isn't a valid JSON array)
                return jsonMessage;
            }
        } catch (JsonSyntaxException e) {
            log.error("Failed to parse message with Gson, returning raw message. JSON: {}", jsonMessage, e);
            // If parsing fails, return the original string to avoid losing data.
            return jsonMessage;
        }

        return plainText.toString().trim();
    }






        public static String getUserNickname(Bot bot, Long userId) {
        ActionData<StrangerInfoResp> resp = bot.getStrangerInfo(userId, true);
        if (resp.getRetCode() == 0) {
            return resp.getData().getNickname();
        }
        return resp.getRetCode() == 0 ? resp.getData().getNickname() : userId.toString();
    }

    public static boolean isAdmin(Bot bot, Long groupId, Long botId) {
        ActionData<GroupMemberInfoResp> groupMemberInfoResp = bot.getGroupMemberInfo(groupId, botId, true);
        if (groupMemberInfoResp.getRetCode() == 0) {
            String role = groupMemberInfoResp.getData().getRole();
            return "admin".equals(role);
        }
        return false;
    }

    public static Long getSessionId(AnyMessageEvent event) {
        return event.getGroupId() != null ? event.getGroupId() : event.getUserId();
    }

    public static Long getSessionId(GroupMessageEvent event) {
        return event.getGroupId();
    }

    public static Long getSessionId(PrivateMessageEvent event) {
        return event.getUserId();
    }
}