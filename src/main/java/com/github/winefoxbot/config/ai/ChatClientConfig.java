package com.github.winefoxbot.config.ai;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.deepseek.DeepSeekChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @author FlanChan (badapple495@outlook.com)
 * @since 2025-12-15-2:16
 */
@Configuration
public class ChatClientConfig {


    @Bean(name = "deepSeekChatClient")
    public ChatClient deepseekChatClient(DeepSeekChatModel deepSeekChatModel) {
        return ChatClient.builder(deepSeekChatModel)
                .build();
    }


    @Bean(name = "openAiChatClient")
    public ChatClient openAiChatClient(OpenAiChatModel openAiChatModel) {
        return ChatClient.builder(openAiChatModel)
                .build();
    }
}