package com.github.winefoxbot.service.bot.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.winefoxbot.service.bot.BotReplyService;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.Random;

@Component
public class BotReplyServiceImpl implements BotReplyService {

    private JsonNode root;
    private final Random random = new Random();

    // 如果希望通过 application.properties 配置 JSON 路径
    private final String configPath = "reply.json"; // 可改为 @Value("${bot.reply.path}")

    private final ObjectMapper objectMapper = new ObjectMapper();

    @PostConstruct
    public void init() throws IOException {
        loadConfig(configPath);
    }


    public void loadConfig(String path) throws IOException {
        root = objectMapper.readTree(getClass().getClassLoader().getResource(path));
    }

    @Override
    public Reply getWelcomeReply(String username) {
        return getReply("welcome", username);
    }

    @Override
    public Reply getFarewellReply(String username) {
        return getReply("farewell", username);
    }

    @Override
    public Reply getMasterStopReply(String username) {
        return getReply("master-stop", username);
    }


    private Reply getReply(String type, String username) {
        if (root == null || !root.has(type)) {
            return new Reply("Hello " + username, null); // 默认值
        }

        JsonNode array = root.get(type);
        if (array.isEmpty()) {
            return new Reply("Hello " + username, null);
        }

        int index = random.nextInt(array.size());
        JsonNode item = array.get(index);

        String text = " " + item.path("text").asText().replace("{username}", username);
        byte [] picture = new byte[0];
        try(InputStream inputStream = getClass().getClassLoader().getResourceAsStream(item.path("picture").asText())) {
            if (inputStream != null) {
                picture = inputStream.readAllBytes();
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return new Reply(text, picture);
    }

    public record Reply(String text, byte [] picture) {}
}
