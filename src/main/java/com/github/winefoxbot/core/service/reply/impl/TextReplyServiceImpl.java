package com.github.winefoxbot.core.service.reply.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.winefoxbot.core.model.dto.TextReply;
import com.github.winefoxbot.core.model.dto.TextReplyParams;
import com.github.winefoxbot.core.service.reply.TextReplyService;
import com.github.winefoxbot.core.utils.DynamicResourceLoader;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.Random;

/**
 * @author FlanChan
 */
@Component
@RequiredArgsConstructor
public class TextReplyServiceImpl implements TextReplyService {

    private JsonNode root;
    private final Random random = new Random();

    private final ObjectMapper objectMapper;

    @PostConstruct
    public void init() throws IOException {
        String configPath = "config/text-reply.json";
        root = objectMapper.readTree(DynamicResourceLoader.getResourceAsString(configPath));
    }

    @Override
    public TextReply getReply(TextReplyParams params) {
        String type = params.getType().getValue();
        String username = params.getUsername() == null ? "用户" : params.getUsername();
        String defaultReply = "Hello %s";
        if (root == null || !root.has(type)) {
            return new TextReply(defaultReply.formatted(username), null);
        }

        JsonNode array = root.get(type);
        if (array.isEmpty()) {
            return new TextReply(defaultReply.formatted(username), null);
        }

        int index = random.nextInt(array.size());
        JsonNode item = array.get(index);

        String text = " " + (item.has("text") ? item.path("text").asText().replace("{username}", username) : "你好，%s".formatted(username));
        byte[] picture = null;
        String pictureSrc = item.has("picture") ? item.path("picture").asText() : null;
        if (pictureSrc == null || pictureSrc.isEmpty()) {
            return new TextReply(text, null);
        }
        try (InputStream inputStream = DynamicResourceLoader.getInputStream(item.path("picture").asText())) {
            if (inputStream != null) {
                picture = inputStream.readAllBytes();
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return new TextReply(text, picture);
    }
}
