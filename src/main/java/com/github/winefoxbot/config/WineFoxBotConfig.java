package com.github.winefoxbot.config;

import jakarta.annotation.PostConstruct;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * @author FlanChan (badapple495@outlook.com)
 * @since 2025-12-08-21:12
 */
@Configuration
@Data
@ConfigurationProperties(prefix = "winefox")
public class WineFoxBotConfig {
    /**
     * 机器人主人QQ号
     */
    private Long master;
    /**
     * 机器人昵称QQ号
     */
    private List<Long> bot = new ArrayList<>();
    /**
     * 系统提示词
     */
    private String systemPrompt;
    /**
     * 允许使用机器人的群组列表
     */
    private List<Long> allowGroups = new ArrayList<>();

    /**
     * 拦截的命令列表（支持正则表达式）
     */
    private List<String> blockCommands = new ArrayList<>();

    /**
     * 管理员用户列表 (QQ号)
     */
    private List<Long> adminUsers = new ArrayList<>();

    private String testPath;


    @PostConstruct
    public void loadSystemPrompt() throws IOException {
        String systemPromptTemplate = new String(Objects.requireNonNull(getClass().getClassLoader().getResourceAsStream("system_prompt")).readAllBytes());
        // bot ids
        String result = bot.stream()
                .map(id -> "\"" + id + "\"")
                .collect(Collectors.joining(","));
        systemPromptTemplate = systemPromptTemplate.replace("{bot_uids}", result);
        this.systemPrompt = systemPromptTemplate;
    }

    /**
     * 匹配拦截命令
     * @param command
     * @return
     */
    public boolean matchBlockCommand(String command) {
        for (String pattern : blockCommands) {
            if (Pattern.matches(pattern, command)) {
                return true;
            }
        }
        return false;
    }
}