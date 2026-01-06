package com.github.winefoxbot.plugins.chat.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.winefoxbot.core.config.app.WineFoxBotProperties;
import com.github.winefoxbot.core.config.app.WineFoxBotRobotProperties;
import com.github.winefoxbot.core.config.helpdoc.HelpDocConfiguration;
import com.github.winefoxbot.core.model.dto.HelpData;
import com.github.winefoxbot.core.utils.ResourceLoader;
import com.mikuac.shiro.core.BotContainer;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;

/**
 * @author FlanChan (badapple495@outlook.com)
 * @since 2026-01-01-16:45
 */
@Configuration
@Import(WineFoxBotChatProperties.class)
@RequiredArgsConstructor
public class WineFoxBotChatConfig {
    @Getter
    private String systemPrompt;
    private final BotContainer botContainer;
    private final WineFoxBotProperties wineFoxBotProperties;
    private final WineFoxBotChatProperties wineFoxBotChatProperties;
    private final HelpDocConfiguration helpDocConfiguration;
    private final ObjectMapper objectMapper;

    @PostConstruct
    public void init() throws IOException {
        loadSystemPrompt();
        loadAvatarInfo();
        loadHelpDocs();
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
                .replace("{bot_self_id}", botContainer.robots.values().stream().map(id -> "\"" + id + "\"").collect(Collectors.joining(",")))
                .replace("{nickname}", robotProps.getNickname())
                .replace("{masterName}",robotProps.getMasterName())
                .replace("{masters}", robotProps.getSuperUsers().stream().map(Object::toString).collect(Collectors.joining(",")));
    }

    private void loadHelpDocs() {
        HelpData helpData = helpDocConfiguration.getHelpData();
        try {
            String helpDataJson = objectMapper.writeValueAsString(helpData);
            this.systemPrompt = systemPrompt.replace("{help_doc}", helpDataJson);
        } catch (IOException e) {
            throw new RuntimeException("Failed to serialize help data to JSON", e);
        }
    }
}