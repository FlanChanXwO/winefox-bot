package com.github.winefoxbot.plugins.chat.manager;

import com.github.winefoxbot.plugins.chat.model.dto.EmoteResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * 专门负责表情包的语义检索服务
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class EmoteManager {

    private final VectorStore vectorStore;


    /**
     * 在向量库中搜索最匹配的表情
     *
     * @param query 用户当前的上下文或意图描述
     * @return 匹配到的表情包信息，如果没有达到相似度阈值则返回 null
     */
    public EmoteResponse searchBestMatch(String query) {
        // 1. 构建搜索请求
        // topK(1): 我们只需要最合适的那一张
        // similarityThreshold(0.65): 设置一个相似度门槛，防止AI在完全不相关的语境下乱发图
        List<Document> results = vectorStore.similaritySearch(
                SearchRequest.builder()
                        .query(query)
                        .topK(1)
                        .similarityThreshold(0.65)
                        .build()
        );

        if (results.isEmpty()) {
            log.debug("未找到与 '{}' 匹配度足够的表情包", query);
            return null;
        }

        Document bestMatch = results.get(0);
        Map<String, Object> metadata = bestMatch.getMetadata();

        // 从 metadata 中提取我们在 Loader 里存入的数据
        // 注意：不同 VectorStore 实现对于数字类型的存储可能不同（Long/Integer/Double），这里做安全转换
        int id = 0;
        if (metadata.get("id") instanceof Number n) {
            id = n.intValue();
        }

        String path = (String) metadata.getOrDefault("path", "");

        // 我们可以把原来的 description 也取出来返回给 AI，加强它的理解
        // 但我们在 Loader 里把 description 混进了 content，没单独存 metadata。
        // 如果需要，可以在 Loader 的 metadata 里多存一个 "desc" 字段。
        // 这里暂时只返回 path。

        log.info("表情包匹配成功: Query='{}' -> ID={}, Path={}", query, id, path);

        return new EmoteResponse(id, path, "匹配成功");
    }
}
