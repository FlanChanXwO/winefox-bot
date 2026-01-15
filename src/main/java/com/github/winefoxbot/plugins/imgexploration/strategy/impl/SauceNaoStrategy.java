package com.github.winefoxbot.plugins.imgexploration.strategy.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.winefoxbot.plugins.imgexploration.model.dto.SearchResultItemDTO;
import com.github.winefoxbot.plugins.imgexploration.strategy.ImageSearchStrategy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.stereotype.Component;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

/**
 * @author FlanChan
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SauceNaoStrategy implements ImageSearchStrategy {

    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final ExecutorService virtualThreadExecutor;
    private final static String BASE_URL = "https://saucenao.com/search.php";

    @Override
    public String getServiceName() {
        return "SauceNAO";
    }
    // f3d9e10bf9cabeaff55f80a9cf44ba8c2ef59720

    @Override
    public CompletableFuture<List<SearchResultItemDTO>> search(String imgUrl, String apiKey) {
        return CompletableFuture.supplyAsync(() -> {
            if (apiKey == null || apiKey.isBlank()) {
                return Collections.emptyList();
            }
            List<SearchResultItemDTO> results = new ArrayList<>();
            try {
                log.info("SauceNAO searching...");
                HttpUrl httpUrl = HttpUrl.parse(BASE_URL);
                HttpUrl.Builder builder = httpUrl.newBuilder();
                builder.addQueryParameter("db", "999");
                builder.addQueryParameter("output_type", "2");
                builder.addQueryParameter("numres", "5");
                builder.addQueryParameter("api_key", apiKey);
                builder.addQueryParameter("url", URLEncoder.encode(imgUrl, StandardCharsets.UTF_8));

                String apiUrl = builder.build().toString();
                Request request = new Request.Builder()
                        .url(apiUrl)
                        .get()
                        .build();
                try (Response response = httpClient.newCall(request).execute()) {
                    if (response.isSuccessful()) {
                        ResponseBody body = response.body();
                        if (body == null) {
                            return results;
                        }
                        JsonNode root = objectMapper.readTree(body.string());
                        if (root != null && root.has("results")) {
                            for (JsonNode node : root.get("results")) {
                                JsonNode header = node.get("header");
                                JsonNode data = node.get("data");
                                double similarity = Double.parseDouble(header.get("similarity").asText());

                                if (similarity < 60) continue; // 过滤低相似度

                                String title = extractTitle(data);
                                String extUrl = data.has("ext_urls") ? data.get("ext_urls").get(0).asText() : "";
                                String thumbnail = header.get("thumbnail").asText();

                                results.add(new SearchResultItemDTO(
                                        title, extUrl, thumbnail, null, "saucenao",
                                        String.format("%.2f%%", similarity), "", ""
                                ));
                            }
                        }
                    }
                }


            } catch (Exception e) {
                log.error("SauceNAO error", e);
            }
            return results;
        }, virtualThreadExecutor);
    }

    private String extractTitle(JsonNode data) {
        if (data.has("title")) return data.get("title").asText();
        if (data.has("eng_name")) return data.get("eng_name").asText();
        if (data.has("material")) return data.get("material").asText();
        return "SauceNAO Result";
    }
}
