package com.github.winefoxbot.plugins.chat.service;

import com.knuddels.jtokkit.Encodings;
import com.knuddels.jtokkit.api.Encoding;
import com.knuddels.jtokkit.api.EncodingRegistry;
import com.knuddels.jtokkit.api.ModelType;
import org.springframework.ai.chat.messages.Message;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;

import java.util.List;

@Service
public class TokenCounterService {

    private EncodingRegistry registry;

    @PostConstruct
    public void init() {
        // 初始化注册表，这是一个相对昂贵的操作，Spring 单例模式正好适合
        this.registry = Encodings.newDefaultEncodingRegistry();
    }

    /**
     * 计算单个字符串的 Token 数量
     */
    public int countTokens(String text, String modelName) {
        if (text == null || text.isEmpty()) {
            return 0;
        }

        // 自动匹配模型对应的编码器 (例如 gpt-4o 使用 o200k_base)
        var encoding = registry.getEncodingForModel(modelName)
                .orElse(registry.getEncodingForModel(ModelType.GPT_4)); // 默认回退

        return encoding.countTokens(text);
    }

    /**
     * 计算对话列表（Messages）的 Token 数量 (模拟 OpenAI 的计算逻辑)
     * 不同模型的 overhead 可能不同，这里以 GPT-3.5/4 的通用逻辑为例
     */
    public int countMessageTokens(List<Message> messages, String modelName) {
        var encoding = registry.getEncodingForModel(modelName)
                .orElse(registry.getEncodingForModel(ModelType.GPT_4));

        int tokensPerMessage = 3; // GPT-3.5-turbo 和 GPT-4 的通用开销
        int tokensPerName = 1;

        int totalTokens = 0;

        for (var msg : messages) {
            totalTokens += tokensPerMessage;
            totalTokens += encoding.countTokens(msg.getText());
        }

        totalTokens += 3; // 每一个回复都以 <|start|>assistant<|message|> 启动
        return totalTokens;
    }
}
