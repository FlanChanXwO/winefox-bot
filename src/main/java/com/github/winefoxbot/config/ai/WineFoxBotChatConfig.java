package com.github.winefoxbot.config.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.winefoxbot.config.HelpDocConfiguration;
import com.github.winefoxbot.config.app.WineFoxBotProperties;
import com.github.winefoxbot.config.app.WineFoxBotRebotProperties;
import com.github.winefoxbot.model.dto.helpdoc.HelpData;
import com.mikuac.shiro.core.BotContainer;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import java.io.IOException;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * @author FlanChan (badapple495@outlook.com)
 * @since 2026-01-01-16:45
 */
@Configuration
@Import(WineFoxBotChatProperties.class)
@RequiredArgsConstructor
public class WineFoxBotChatConfig {
    /**
     * 系统提示词
     */
    @Getter
    private String systemPrompt;

    private final BotContainer botContainer;

    private final WineFoxBotProperties wineFoxBotProperties;

    private final HelpDocConfiguration helpDocConfiguration;

    private final ObjectMapper objectMapper;

    @PostConstruct
    public void loadSystemPrompt() throws IOException {
        String systemPromptTemplate = new String(Objects.requireNonNull(getClass().getClassLoader().getResourceAsStream("system_prompt")).readAllBytes());
        // bot ids
        WineFoxBotRebotProperties robotProps = wineFoxBotProperties.getRobot();
        HelpData helpData = helpDocConfiguration.getHelpData();
        String result = botContainer.robots.values().stream()
                .map(id -> "\"" + id + "\"")
                .collect(Collectors.joining(","));
        systemPromptTemplate = systemPromptTemplate.replace("{bot_uids}", result)
                .replace("{nickname}",robotProps.getNickname())
                .replace("{superusers}", robotProps.getSuperUsers().stream().map(Object::toString).collect(Collectors.joining(",")))
                .replace("{help_doc}",objectMapper.writeValueAsString(helpData));
        this.systemPrompt = systemPromptTemplate;
    }
}