package com.github.winefoxbot.plugins.gscore;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.winefoxbot.core.annotation.plugin.Plugin;
import com.github.winefoxbot.plugins.gscore.client.GsuidCoreClient;
import com.github.winefoxbot.plugins.gscore.config.GsuidPluginConfig;
import com.github.winefoxbot.plugins.gscore.model.CoreMessage;
import com.github.winefoxbot.plugins.gscore.model.MsgNode;
import com.mikuac.shiro.annotation.GroupMessageHandler;
import com.mikuac.shiro.annotation.GuildMessageHandler;
import com.mikuac.shiro.annotation.PrivateMessageHandler;
import com.mikuac.shiro.core.Bot;
import com.mikuac.shiro.dto.event.message.GroupMessageEvent;
import com.mikuac.shiro.dto.event.message.GuildMessageEvent;
import com.mikuac.shiro.dto.event.message.PrivateMessageEvent;
import com.mikuac.shiro.model.ArrayMsg;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.scheduling.annotation.Async;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Plugin(name = "gsuid 适配器", description = "基于shiro bot框架连接gscore核心")
@RequiredArgsConstructor
@ConditionalOnBooleanProperty("winefoxbot.plugins.gsuid.enable")
public class GsuidConnectorPlugin {

    private final GsuidCoreClient coreClient;

    private final ObjectMapper objectMapper;

    private final GsuidPluginConfig config;


    // 处理群消息
    @Async
    @GroupMessageHandler
    public void onGroupMessage(Bot bot, GroupMessageEvent event) {
        if (coreClient.isClosed()) return;

        CoreMessage msg = new CoreMessage();
        msg.setBotId("onebot");
        msg.setBotSelfId(String.valueOf(bot.getSelfId()));
        msg.setMsgId(String.valueOf(event.getMessageId()));
        msg.setUserType("group");
        msg.setGroupId(String.valueOf(event.getGroupId()));
        msg.setUserId(String.valueOf(event.getUserId()));

        // 权限映射
        String role = event.getSender().getRole();
        int userPm = 6; // 默认普通群员
        if ("owner".equals(role)) {
            userPm = 2;
        } else if ("admin".equals(role)) {
            userPm = 3;
        }

        // 检查是否为超级用户，如果是则覆盖为 1
        if (config.getSuperUsers().contains(event.getUserId())) {
            userPm = 1;
        }

        msg.setUserPm(userPm);

        msg.setSender(buildSender(event.getSender(), String.valueOf(event.getUserId())));

        // 解析 Shiro 的 ArrayMsg 转换为 Core 需要的 Node 格式
        List<MsgNode> nodes = convertMessage(event.getArrayMsg());
        msg.setContent(nodes);

        // 发送给 Core
        try {
            coreClient.send(objectMapper.writeValueAsBytes(msg));
        } catch (Exception e) {
            log.error("发送群消息失败", e);
        }

    }

    // 处理私聊消息
    @Async
    @PrivateMessageHandler
    public void onPrivateMessage(Bot bot, PrivateMessageEvent event) {
        if (coreClient.isClosed()) return;

        CoreMessage msg = new CoreMessage();
        msg.setBotId("onebot");
        msg.setBotSelfId(String.valueOf(bot.getSelfId()));
        msg.setMsgId(String.valueOf(event.getMessageId()));
        msg.setUserType("direct");
        msg.setUserId(String.valueOf(event.getUserId()));

        int userPm = 6; // 私聊默认普通用户
        // 检查是否为超级用户，如果是则覆盖为 1
        if (config.getSuperUsers().contains(event.getUserId())) {
            userPm = 1;
        }
        msg.setUserPm(userPm);

        msg.setSender(buildSender(event.getPrivateSender(), String.valueOf(event.getUserId())));

        List<MsgNode> nodes = convertMessage(event.getArrayMsg());
        msg.setContent(nodes);

        try {
            coreClient.send(objectMapper.writeValueAsBytes(msg));
        } catch (Exception e) {
            log.error("发送私聊消息失败", e);
        }
    }

    // 处理频道消息
    @Async
    @GuildMessageHandler
    public void onGuildMessage(Bot bot, GuildMessageEvent event) {
        if (coreClient.isClosed()) return;

        CoreMessage msg = new CoreMessage();
        msg.setBotId(config.getBotId());
        msg.setBotSelfId(String.valueOf(bot.getSelfId()));
        msg.setMsgId(event.getMessageId());
        msg.setUserType("channel"); // 对应频道
        msg.setGroupId(event.getGuildId()); // 频道ID作为GroupId
        // 注意：GuildMessageEvent 的 sender 里面有 user_id 和 tiny_id
        // 这里优先使用 user_id，如果没有则使用 tiny_id
        String userId = String.valueOf(event.getSender().getUserId());
        if (userId == null || "0".equals(userId) || "null".equals(userId)) {
            userId = event.getSender().getTinyId();
        }
        msg.setUserId(userId);

        // 频道权限映射
        // GuildMessageEvent 目前没有直接暴露 role 字段，暂时默认为普通成员 6
        // 如果有超级用户判断逻辑，应在此处添加，若为超级用户则设为 1
        int userPm = 6;
        if (config.getSuperUsers().contains(event.getSender().getUserId())) {
            userPm = 1;
        }
        msg.setUserPm(userPm);

        msg.setSender(buildSender(event.getSender(), userId));

        List<MsgNode> nodes = convertMessage(event.getArrayMsg());
        msg.setContent(nodes);

        try {
            coreClient.send(objectMapper.writeValueAsBytes(msg));
        } catch (Exception e) {
            log.error("发送频道消息失败", e);
        }
    }

    private List<MsgNode> convertMessage(List<ArrayMsg> arrayMsgs) {
        List<MsgNode> nodes = new ArrayList<>();
        if (arrayMsgs == null) return nodes;

        for (ArrayMsg am : arrayMsgs) {
            String type = am.getType().name();
            MsgNode node = new MsgNode();
            node.setType(type);

            // 根据不同类型提取数据
            switch (type) {
                case "text" -> node.setData(am.getStringData("text"));
                case "image" -> node.setData(am.getStringData("url"));
                case "at" -> node.setData(am.getStringData("qq"));
                case "face", "reply" -> node.setData(am.getStringData("id"));
                default -> {
                    // 其他类型尝试直接转 map 或者保留原始数据
                    try {
                        // 尝试将 JsonNode 转为 Map
                        Map<String, Object> dataMap = objectMapper.convertValue(am.getData(), new TypeReference<Map<String, Object>>() {
                        });
                        node.setData(dataMap);
                    } catch (Exception e) {
                        node.setData(am.getData().toString());
                    }
                }
            }
            nodes.add(node);
        }
        return nodes;
    }

    private Map<String, Object> buildSender(Object senderObj, String userId) {
        Map<String, Object> map = objectMapper.convertValue(senderObj, new TypeReference<Map<String, Object>>() {
        });
        if (map == null) {
            map = new HashMap<>();
        }
        map.put("user_id", userId);
        map.put("avater", "http://q1.qlogo.cn/g?b=qq&nk=" + userId + "&s=640");
        map.putIfAbsent("nickname", "");
        map.putIfAbsent("card", "");
        map.putIfAbsent("role", "member");
        return map;
    }
}