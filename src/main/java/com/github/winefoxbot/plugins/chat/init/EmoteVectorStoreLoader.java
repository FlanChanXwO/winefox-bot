package com.github.winefoxbot.plugins.chat.init;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.winefoxbot.plugins.chat.model.dto.EmoteData;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * 在应用启动时，读取 JSON，将 intent（意图）和 description（描述）转化为向量并存储。
 *
 * @author FlanChan
 */
@ConditionalOnBooleanProperty("winefox.ai.chat.enable-emoji")
@Component
@Slf4j
@RequiredArgsConstructor
public class EmoteVectorStoreLoader {

    private final VectorStore vectorStore;
    private final ObjectMapper objectMapper;
    private final JdbcClient jdbcClient;

    @EventListener(ApplicationReadyEvent.class)
    public void init() throws IOException {
        List<EmoteData> allEmotes = Arrays.asList(
                objectMapper.readValue(new ClassPathResource("config/emoji.json").getInputStream(), EmoteData[].class)
        );

        // 1. 从数据库查出所有已存在的 ID (假设 metadata 字段里存了原始 id)
        // 注意：PostgreSQL 的 JSONB 查询语法
        List<String> existingIds = jdbcClient.sql("SELECT metadata->>'id' FROM vector_store")
                .query(String.class)
                .list();

        // 2. 过滤掉已经存在的，只保留新增的
        List<Document> newDocuments = allEmotes.stream()
                .filter(emote -> !existingIds.contains(emote.id())) // 只留新 ID
                .map(emoteData -> {
                    String content = "意图: " + emoteData.intent() +
                            " | 描述: " + emoteData.description() +
                            " | 关键词: " + String.join(", ", emoteData.keywords());
                    Map<String, Object> metadata = Map.of(
                            "id", emoteData.id(),
                            "path", emoteData.path()
                    );
                    // 这里的 Document ID 建议和 emoteData.id 保持一致，方便管理
                    return new Document(content, metadata);
                })
                .toList();

        // 3. 如果有新数据，才执行 embedding 和 存储
        if (!newDocuments.isEmpty()) {
            vectorStore.add(newDocuments);
            log.info("新增加载了 {} 个表情包向量数据！", newDocuments.size());
        } else {
            log.info("数据已是最新，无需加载。");
        }
    }

}
