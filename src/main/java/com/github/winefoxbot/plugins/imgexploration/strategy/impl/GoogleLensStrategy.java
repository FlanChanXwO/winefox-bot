package com.github.winefoxbot.plugins.imgexploration.strategy.impl;

import com.github.winefoxbot.plugins.imgexploration.config.ImgExplorationConfig;
import com.github.winefoxbot.plugins.imgexploration.model.dto.SearchResultItemDTO;
import com.github.winefoxbot.plugins.imgexploration.strategy.ImageSearchStrategy;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient; // 引入 OkHttp
import okhttp3.Request;
import okhttp3.Response;
import org.springframework.stereotype.Component;
import serpapi.SerpApiSearch;
import serpapi.SerpApiSearchException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author FlanChan
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GoogleLensStrategy implements ImageSearchStrategy {

    private final ExecutorService virtualThreadExecutor;
    private final ImgExplorationConfig imgExplorationConfig;

    // 1. 注入你的 OkHttpClient Bean
    private final OkHttpClient okHttpClient;

    private final AtomicInteger currentKeyIndex = new AtomicInteger(0);

    // 自定义 SDK 类，强制使用 google_lens 引擎
    public static class GoogleLensSDKSearch extends SerpApiSearch {
        public GoogleLensSDKSearch(Map<String, String> parameter) {
            super(parameter, "google_lens");
        }
    }

    @Override
    public String getServiceName() {
        return "GoogleLens(Multi-Key)";
    }

    @Override
    public CompletableFuture<List<SearchResultItemDTO>> search(String imgUrl) {
        return CompletableFuture.supplyAsync(() -> {
            List<SearchResultItemDTO> resultList = new ArrayList<>();

            String effectiveApiKey;
            try {
                effectiveApiKey = selectViableApiKey();
            } catch (RuntimeException e) {
                log.error("Search aborted: {}", e.getMessage());
                throw e;
            }

            if (isLocalFile(imgUrl)) {
                log.error("SerpApi SDK does not support local file paths: {}", imgUrl);
                return resultList;
            }

            try {
                log.info("Starting Google Lens search with key ...{}", effectiveApiKey.substring(Math.max(0, effectiveApiKey.length() - 4)));

                Map<String, String> parameter = new HashMap<>();
                parameter.put("api_key", effectiveApiKey);
                parameter.put("engine", "google_lens"); // 显式声明引擎
                parameter.put("url", imgUrl);
                parameter.put("hl", "zh-cn");

                // 使用自定义类发起请求 (注意：这一步还是会走 SDK 内部的 HTTP 逻辑)
                GoogleLensSDKSearch search = new GoogleLensSDKSearch(parameter);
                JsonObject results = search.getJson();

                if (results.has("visual_matches")) {
                    JsonArray matches = results.getAsJsonArray("visual_matches");
                    int limit = Math.min(matches.size(), 8);

                    for (int i = 0; i < limit; i++) {
                        try {
                            JsonObject match = matches.get(i).getAsJsonObject();
                            String title = getJsonString(match, "title");
                            String link = getJsonString(match, "link");
                            String source = getJsonString(match, "source");
                            String thumbnail = getJsonString(match, "thumbnail");

                            if (title.isBlank() || link.isBlank()) continue;

                            // 2. 调用修改后的下载方法，使用 OkHttp
                            byte[] imgBytes = downloadImageBytes(thumbnail);

                            if (imgBytes != null && imgBytes.length > 0) {
                                resultList.add(new SearchResultItemDTO(title, link, source, imgBytes, "Google Lens"));
                            }
                        } catch (Exception e) {
                            log.warn("Error parsing item", e);
                        }
                    }
                } else {
                    log.info("No visual matches found.");
                    if (results.has("error")) {
                        log.error("SerpApi Error: {}", results.get("error").getAsString());
                    }
                }

            } catch (SerpApiSearchException e) {
                log.error("SerpApi SDK execution failed", e);
            } catch (Exception e) {
                log.error("Unexpected error", e);
            }
            return resultList;
        }, virtualThreadExecutor);
    }

    private synchronized String selectViableApiKey() {
        List<String> keys = imgExplorationConfig.getSerpApikeys();
        if (keys == null || keys.isEmpty()) throw new RuntimeException("No API keys configured");

        int startIdx = currentKeyIndex.get() % keys.size();
        int loopCount = 0;

        while (loopCount < keys.size()) {
            int actualIndex = (startIdx + loopCount) % keys.size();
            String candidateKey = keys.get(actualIndex);

            // 检查 Key 余额
            int searchesLeft = getRemainingSearches(candidateKey);

            if (searchesLeft > 0) {
                currentKeyIndex.set(actualIndex);
                return candidateKey;
            }
            loopCount++;
        }
        throw new RuntimeException("All API keys exhausted!");
    }

    /**
     * 使用 OkHttpClient 检查剩余次数
     */
    private int getRemainingSearches(String apiKey) {
        String url = "https://serpapi.com/account?api_key=" + apiKey;
        Request request = new Request.Builder()
                .url(url)
                .get()
                .build();

        // 必须使用 try-with-resources 自动关闭 ResponseBody，避免连接泄漏
        try (Response response = okHttpClient.newCall(request).execute()) {
            if (response.isSuccessful() && response.body() != null) {
                String responseBody = response.body().string();
                // 依然使用 Gson 解析
                JsonObject json = JsonParser.parseString(responseBody).getAsJsonObject();
                if (json.has("total_searches_left")) {
                    return json.get("total_searches_left").getAsInt();
                }
            }
        } catch (Exception e) {
            log.warn("Check quota failed for key ending in ...{}", apiKey.substring(Math.max(0, apiKey.length() - 4)), e);
        }
        return -1;
    }

    /**
     * 使用 OkHttpClient 下载图片
     */
    private byte[] downloadImageBytes(String imageUrl) {
        if (imageUrl == null || imageUrl.isBlank()) return null;

        Request request = new Request.Builder()
                .url(imageUrl)
                .get()
                .build();

        try (Response response = okHttpClient.newCall(request).execute()) {
            if (response.isSuccessful() && response.body() != null) {
                return response.body().bytes();
            }
        } catch (Exception ignored) {
            // 下载失败忽略，不中断流程
        }
        return null;
    }

    private boolean isLocalFile(String url) {
        return url == null || url.startsWith("file:") || !url.startsWith("http");
    }

    private String getJsonString(JsonObject obj, String key) {
        if (obj.has(key) && !obj.get(key).isJsonNull()) return obj.get(key).getAsString();
        return "";
    }
}
