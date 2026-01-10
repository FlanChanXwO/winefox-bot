package com.github.winefoxbot.plugins.chat.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.winefoxbot.core.config.app.WineFoxBotProperties;
import com.github.winefoxbot.core.config.app.WineFoxBotRobotProperties;
import com.github.winefoxbot.core.init.HelpDocLoader;
import com.github.winefoxbot.core.model.dto.HelpData;
import com.github.winefoxbot.core.model.dto.HelpGroup;
import com.github.winefoxbot.core.utils.ResourceLoader;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author FlanChan (badapple495@outlook.com)
 * @since 2026-01-01-16:45
 */
@Slf4j
@Configuration
@Import(WineFoxBotChatProperties.class)
@RequiredArgsConstructor
public class WineFoxBotChatConfig {
    @Getter
    private String systemPrompt;
    private final HelpDocLoader helpDocLoader;
    private final WineFoxBotProperties wineFoxBotProperties;
    private final WineFoxBotChatProperties wineFoxBotChatProperties;
    private final ObjectMapper objectMapper;
    private final List<String> aiToolNames;

    @Order(Ordered.LOWEST_PRECEDENCE)
    @EventListener(ContextRefreshedEvent.class)
    public void init() {
        try {
            loadSystemPrompt();
            loadAvatarInfo();
            // 此时 HelpDocLoader 已经通过事件监听器加载完数据了
            loadHelpDocs();
            loadToolTips();
            log.info("WineFoxBotChatConfig system prompt initialized successfully.");
        } catch (Exception e) {
            log.error("Failed to initialize WineFoxBotChatConfig system prompt.", e);
        }
    }

    private void loadSystemPrompt() {
        String avatarDir = wineFoxBotChatProperties.getAvatarDir();
        String avatar = wineFoxBotChatProperties.getAvatar();

        try (InputStream inputStream = ResourceLoader.getInputStream(avatarDir + File.separator + avatar + File.separator + WineFoxBotChatProperties.AVATAR_FILE_NAME)) {
            this.systemPrompt = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException("Failed to load system prompt from " + avatarDir, e);
        }
    }

    private void loadAvatarInfo() {
        WineFoxBotRobotProperties robotProps = wineFoxBotProperties.getRobot();

        this.systemPrompt = systemPrompt
                .replace("{bot_self_id}", robotProps.getBotId())
                .replace("{nickname}", robotProps.getNickname())
                .replace("{masterName}",robotProps.getMasterName())
                .replace("{masters}", robotProps.getSuperUsers().stream().map(Object::toString).collect(Collectors.joining(",")));
    }

    private void loadHelpDocs() {
        List<HelpGroup> groups = helpDocLoader.getSortedHelpData().getGroups();
        try {
            // 1. 将对象列表转换为 JsonNode 树结构
            JsonNode rootNode = objectMapper.valueToTree(groups);

            // 2. 遍历数组节点，移除每个对象的 icon 属性
            if (rootNode.isArray()) {
                for (JsonNode node : rootNode) {
                    if (node instanceof ObjectNode objectNode) {
                        objectNode.remove("icon");
                        objectNode.remove("order");
                    }
                }
            }
            // 3. 将处理后的 JsonNode 序列化为字符串
            String helpDataJson = objectMapper.writeValueAsString(rootNode);
            this.systemPrompt = systemPrompt.replace("{help_doc}", helpDataJson);
        } catch (IOException e) {
            throw new RuntimeException("Failed to serialize help data to JSON", e);
        }
    }

    private void loadToolTips() {
        this.systemPrompt = systemPrompt.replace("{tool_string}", aiToolNames.stream()
                .map(name -> "- " + name)
                .collect(Collectors.joining("\n")));
        log.info("Loaded AI tool names: {}", aiToolNames);
    }

}