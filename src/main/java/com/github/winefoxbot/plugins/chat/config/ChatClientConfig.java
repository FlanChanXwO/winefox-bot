package com.github.winefoxbot.plugins.chat.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Arrays;
import java.util.List;
import java.util.function.Function;

/**
 * @author FlanChan (badapple495@outlook.com)
 * @since 2025-12-15-2:16
 */
@Configuration
public class ChatClientConfig {

    /**
     * 自动扫描容器中所有名称以 "Tool" 结尾的 Function Bean，或者你可以自定义其他规则。
     * 这样只要新写一个工具 Bean，它就会自动出现在这个列表里。
     */
    @Bean("aiToolNames")
    public List<String> aiToolNames(ApplicationContext context) {
        String[] functionBeanNames = context.getBeanNamesForType(Function.class);
        return Arrays.stream(functionBeanNames)
                // 约定：只有 Bean 名称以 "Tool" 结尾（忽略大小写）才会被视为 AI 工具
                // 你的 HelpDocImageTool Bean 名字是 "helpDocumentTool"，符合此规则
                .filter(name -> name.toLowerCase().endsWith("tool"))
                .toList();
    }

    @Bean
    public ChatClient openAiChatClient(ChatClient.Builder builder, List<String> aiToolNames) {
        // 将扫描到的所有工具名称注册为全局默认工具
        return builder
                .defaultToolNames(aiToolNames.toArray(new String[0]))
                .build();
    }
}