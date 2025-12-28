package com.github.winefoxbot.service.bot.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.winefoxbot.model.dto.reply.BotReply;
import com.github.winefoxbot.model.dto.reply.BotReplyParams;
import com.github.winefoxbot.service.bot.BotReplyService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.Random;

@Component
@RequiredArgsConstructor
public class BotReplyServiceImpl implements BotReplyService {

    private JsonNode root;
    private final Random random = new Random();

    // 如果希望通过 application.properties 配置 JSON 路径
    private final String configPath = "config/reply.json"; // 可改为 @Value("${bot.reply.path}")
    private final String defaultReply = "Hello %s";
    private final ObjectMapper objectMapper;

    @PostConstruct
    public void init() throws IOException {
        root = objectMapper.readTree(getClass().getClassLoader().getResource(configPath));
    }

    @Override
    public BotReply getReply(BotReplyParams params) {
        String type = params.getType().getValue();
        String username = params.getUsername() == null ? "用户" : params.getUsername();
        if (root == null || !root.has(type)) {
            return new BotReply(defaultReply.formatted(username), null);
        }

        JsonNode array = root.get(type);
        if (array.isEmpty()) {
            return new BotReply(defaultReply.formatted(username), null);
        }

        int index = random.nextInt(array.size());
        JsonNode item = array.get(index);

        String text = " " + (item.has("text") ? item.path("text").asText().replace("{username}", username) : "你好，%s".formatted(username));
        byte[] picture = null;
        String pictureSrc = item.has("picture") ? item.path("picture").asText() : null;
        if (pictureSrc == null || pictureSrc.isEmpty()) {
            return new BotReply(text, null);
        }
        try (InputStream inputStream = getClass().getClassLoader().getResourceAsStream(item.path("picture").asText())) {
            if (inputStream != null) {
                picture = inputStream.readAllBytes();
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return new BotReply(text, picture);
    }
}
