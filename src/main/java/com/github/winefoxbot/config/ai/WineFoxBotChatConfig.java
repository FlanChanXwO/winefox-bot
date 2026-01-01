package com.github.winefoxbot.config.ai;

import com.github.winefoxbot.config.WineFoxBotConfig;
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

    private final WineFoxBotConfig wineFoxBotConfig;

    @PostConstruct
    public void loadSystemPrompt() throws IOException {
        String systemPromptTemplate = new String(Objects.requireNonNull(getClass().getClassLoader().getResourceAsStream("system_prompt")).readAllBytes());
        // bot ids
        String result = wineFoxBotConfig.getBot().stream()
                .map(id -> "\"" + id + "\"")
                .collect(Collectors.joining(","));
        systemPromptTemplate = systemPromptTemplate.replace("{bot_uids}", result);
        this.systemPrompt = systemPromptTemplate;
    }
}