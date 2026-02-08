package com.github.winefoxbot.plugins.gscore.client;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.winefoxbot.plugins.gscore.config.GsuidPluginConfig;
import com.github.winefoxbot.plugins.gscore.model.CoreSendRequest;
import com.github.winefoxbot.plugins.gscore.model.MsgNode;
import com.mikuac.shiro.common.utils.MsgUtils;
import com.mikuac.shiro.common.utils.ShiroUtils;
import com.mikuac.shiro.core.Bot;
import com.mikuac.shiro.core.BotContainer;
import com.mikuac.shiro.dto.action.common.ActionData;
import com.mikuac.shiro.dto.action.common.MsgId;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.FileOutputStream;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Slf4j
@Component
public class GsuidCoreClient extends WebSocketClient {

    private final ObjectMapper objectMapper;

    private final BotContainer botContainer;

    private final GsuidPluginConfig config;

    public GsuidCoreClient(ObjectMapper objectMapper, BotContainer botContainer, GsuidPluginConfig config) {
        super(URI.create("ws://localhost:8765/ws/ShiroBot"));
        this.objectMapper = objectMapper;
        this.botContainer = botContainer;
        this.config = config;
    }

    @PostConstruct
    public void init() {
        // 从配置中读取连接信息
        String uriStr = String.format("ws://%s:%s/ws/%s", config.getHost(), config.getPort(),config.getBotId());
        this.uri = URI.create(uriStr);
        // 如果有 token，可以添加 header
        if (config.getWsToken() != null && !config.getWsToken().isEmpty()) {
            this.addHeader("Authorization", config.getWsToken());
        }
        new Thread(this::connect).start();
    }

    @Override
    public void onOpen(ServerHandshake handshakedata) {
        log.info("[gsuid] 已连接到 GSUID Core");
    }

    @Override
    public void onMessage(ByteBuffer bytes) {
        try {
            // 1. 将 ByteBuffer 转换为 String (UTF-8)
            String message = new String(bytes.array(), StandardCharsets.UTF_8);
            // 2 复用文本处理逻辑
            onMessage(message);
        } catch (Exception e) {
            log.error("[gsuid] 处理 Core 二进制消息失败", e);
        }
    }


    @Override
    public void onMessage(String message) {
        try {
            log.debug("[gsuid] 收到 Core 消息: {}", message);
            // Jackson 反序列化
            CoreSendRequest req = objectMapper.readValue(message, CoreSendRequest.class);
            handleCoreMessage(req);
        } catch (Exception e) {
            log.error("[gsuid] 解析 Core 消息失败", e);
        }
    }

    @Override
    public void onClose(int code, String reason, boolean remote) {
        log.warn("[gsuid] 与 GSUID Core 断开连接: {}", reason);
        new Thread(() -> {
            try {
                Thread.sleep(5000);
                reconnect();
            } catch (InterruptedException e) {
                log.error("[gsuid] 重连等待被中断", e);
            }
        }).start();
    }

    @Override
    public void onError(Exception ex) {
        log.error("[gsuid] WebSocket 错误", ex);
    }

    // 处理 Core 发来的发送指令 -> 转为 Shiro 发送
    private void handleCoreMessage(CoreSendRequest req) {
        // 获取当前机器人的 Bot 实例 (假设只有一个机器人，或者根据逻辑获取)
        Bot bot = botContainer.robots.get(Long.valueOf(req.getBotSelfId()));
        if (bot == null) {
            log.warn("[gsuid] 无法找到 Bot 实例: {}", req.getBotSelfId());
            return;
        }

        if (req.getContent() == null || req.getContent().isEmpty()) return;

        // 检查是否为 log 消息
        if ("log".equals(req.getContent().get(0).getType())) {
            log.debug("[gsuid] {}", req.getContent().get(0).getData());
            return;
        }

        // 检查是否包含 node 类型的消息，如果有则视为合并转发消息
        boolean isForward = req.getContent().stream().anyMatch(n -> "node".equals(n.getType()));

        if (isForward) {
            List<String> forwardNodes = new ArrayList<>();
            for (MsgNode node : req.getContent()) {
                if ("node".equals(node.getType())) {
                    try {
                        // node 的 data 字段是 Message[] 的 JSON 字符串，需要反序列化
                        // 注意：这里 data 可能是 List<MsgNode> 对象，也可能是 JSON 字符串
                        // 如果是 List<MsgNode> 对象，直接转换
                        List<MsgNode> subNodes;
                        if (node.getData() instanceof List) {
                            subNodes = objectMapper.convertValue(node.getData(), new TypeReference<>() {
                            });
                        } else if (node.getData() instanceof String) {
                            subNodes = objectMapper.readValue((String) node.getData(), new TypeReference<>() {
                            });
                        } else {
                            log.warn("Unknown data type for node: {}", node.getData().getClass());
                            continue;
                        }

                        // 构建子消息链
                        String nodeContent = buildMessageChain(subNodes, bot, req.getTargetType(), req.getTargetId());
                        forwardNodes.add(nodeContent);
                    } catch (Exception e) {
                        log.error("[gsuid] 解析合并转发节点失败: {}", node.getData(), e);
                    }
                }
            }

            if (!forwardNodes.isEmpty()) {
                // 生成合并转发消息结构
                List<Map<String, Object>> forwardMsg = ShiroUtils.generateForwardMsg(bot, forwardNodes);
                if ("group".equals(req.getTargetType())) {
                    bot.sendGroupForwardMsg(Long.parseLong(req.getTargetId()), forwardMsg);
                } else if ("direct".equals(req.getTargetType())) {
                    bot.sendPrivateForwardMsg(Long.parseLong(req.getTargetId()), forwardMsg);
                }
            }
        } else {
            // 普通消息
            String msg = buildMessageChain(req.getContent(), bot, req.getTargetType(), req.getTargetId());
            if (msg != null && !msg.isEmpty()) {
                Optional<ActionData<MsgId>> msgIdActionData = Optional.empty();
                if ("group".equals(req.getTargetType())) {
                    msgIdActionData = Optional.ofNullable(bot.sendGroupMsg(Long.parseLong(req.getTargetId()), msg, false));
                } else if ("direct".equals(req.getTargetType())) {
                    msgIdActionData = Optional.ofNullable(bot.sendPrivateMsg(Long.parseLong(req.getTargetId()), msg, false));
                }
                if (msgIdActionData.isPresent()) {
                    MsgId msgId = msgIdActionData.get().getData();
                    log.debug("[gsuid] 消息发送成功，消息ID: {}", msgId.getMessageId());
                } else {
                    log.warn("[gsuid] 消息发送失败");
                }
            }
        }
    }

    // 构建消息链字符串
    private String buildMessageChain(List<MsgNode> nodes, Bot bot, String targetType, String targetId) {
        MsgUtils builder = MsgUtils.builder();
        for (MsgNode node : nodes) {
            switch (node.getType()) {
                case "text":
                case "markdown":
                    if (node.getData() instanceof String) {
                        builder.text((String) node.getData());
                    }
                    break;
                case "image":
                    if (node.getData() instanceof String imgData) {
                        if (imgData.startsWith("http")) {
                            builder.img(imgData);
                        } else if (imgData.startsWith("base64://")) {
                            // Shiro 支持 Base64 图片
                            builder.img(imgData);
                        } else {
                            // 纯 Base64 字符串处理
                            builder.img("base64://" + imgData);
                        }
                    }
                    break;
                case "at":
                    try {
                        if (node.getData() instanceof String) {
                            builder.at(Long.parseLong((String) node.getData()));
                        } else if (node.getData() instanceof Number) {
                            builder.at(((Number) node.getData()).longValue());
                        }
                    } catch (NumberFormatException e) {
                        log.warn("[gsuid] Invalid at target: {}", node.getData());
                    }
                    break;
                case "reply":
                    try {
                        if (node.getData() instanceof String) {
                            builder.reply(Integer.parseInt((String) node.getData()));
                        } else if (node.getData() instanceof Number) {
                            builder.reply(((Number) node.getData()).intValue());
                        }
                    } catch (NumberFormatException e) {
                        log.warn("[gsuid] Invalid reply id: {}", node.getData());
                    }
                    break;
                case "record":
                    if (node.getData() instanceof String) {
                        builder.voice((String) node.getData());
                    }
                    break;
                case "file":
                    // 处理文件上传
                    if (node.getData() instanceof String) {
                        handleFileUpload((String) node.getData(), bot, targetType, targetId);
                    }
                    break;
                default:
                    break;
            }
        }
        return builder.build();
    }

    private void handleFileUpload(String data, Bot bot, String targetType, String targetId) {
        try {
            // data 格式: {文件名}|{文件base64}
            int separatorIndex = data.indexOf("|");
            if (separatorIndex == -1) {
                log.warn("[gsuid] Invalid file data format. Expected {filename}|{base64}");
                return;
            }
            String fileName = data.substring(0, separatorIndex);
            String base64Content = data.substring(separatorIndex + 1);

            // 解码 Base64 并保存为临时文件
            byte[] fileBytes = Base64.getDecoder().decode(base64Content);
            File tempFile = File.createTempFile("upload_", "_" + fileName);
            try (FileOutputStream fos = new FileOutputStream(tempFile)) {
                fos.write(fileBytes);
            }

            // 上传文件
            if ("group".equals(targetType)) {
                bot.uploadGroupFile(Long.parseLong(targetId), tempFile.getAbsolutePath(), fileName);
            } else if ("direct".equals(targetType)) {
                bot.uploadPrivateFile(Long.parseLong(targetId), tempFile.getAbsolutePath(), fileName);
            }

            tempFile.deleteOnExit();

        } catch (Exception e) {
            log.error("[gsuid] 文件上传失败", e);
        }
    }
}