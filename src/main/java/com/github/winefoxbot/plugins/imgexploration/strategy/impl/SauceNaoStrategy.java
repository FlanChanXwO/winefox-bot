package com.github.winefoxbot.plugins.imgexploration.strategy.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.winefoxbot.plugins.imgexploration.config.ImgExplorationConfig;
import com.github.winefoxbot.plugins.imgexploration.model.dto.SearchResultItemDTO;
import com.github.winefoxbot.plugins.imgexploration.strategy.ImageSearchStrategy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
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
    private final ImgExplorationConfig imgExplorationConfig;
    private final static String BASE_URL = "https://saucenao.com/search.php";

    @Override
    public String getServiceName() {
        return "SauceNAO";
    }

    @Override
    public CompletableFuture<List<SearchResultItemDTO>> search(String imgUrlOrPath) {
        return CompletableFuture.supplyAsync(() -> {
            String apiKey = imgExplorationConfig.getSauceNaoApiKey();
            if (apiKey == null || apiKey.isBlank()) {
                return Collections.emptyList();
            }
            List<SearchResultItemDTO> results = new ArrayList<>();
            try {
                // 1. 初始化 Multipart 构建器
                MultipartBody.Builder multipartBuilder = new MultipartBody.Builder()
                        .setType(MultipartBody.FORM)
                        .addFormDataPart("api_key", apiKey)
                        .addFormDataPart("output_type", "2")
                        .addFormDataPart("numres", "5");

                // 2. 根据输入类型决定是 "传URL" 还是 "传文件"
                boolean isRemoteUrl = imgUrlOrPath.startsWith("http://") || imgUrlOrPath.startsWith("https://");

                if (isRemoteUrl) {
                    log.info("SauceNAO searching via Remote URL: {}", imgUrlOrPath);
                    // 策略 A: 直接传递 URL，让 SauceNAO 自己去下载
                    multipartBuilder.addFormDataPart("url", imgUrlOrPath);

                    // 可选：为了完全模拟你的 curl 行为，有些后端可能强制要求 file 字段存在，即使为空
                    // multipartBuilder.addFormDataPart("file", "", RequestBody.create(new byte[0], MediaType.parse("application/octet-stream")));
                } else {
                    log.info("SauceNAO searching via Local File Upload: {}", imgUrlOrPath);
                    // 策略 B: 本地文件，读取字节并上传
                    byte[] imageBytes = getLocalImageBytes(imgUrlOrPath);
                    if (imageBytes == null || imageBytes.length == 0) {
                        log.warn("Failed to read local file: {}", imgUrlOrPath);
                        return results;
                    }
                    RequestBody fileBody = RequestBody.create(imageBytes, MediaType.parse("image/jpeg"));
                    multipartBuilder.addFormDataPart("file", "image.jpg", fileBody);
                }

                MultipartBody requestBody = multipartBuilder.build();

                // 3. 构建 POST 请求
                Request request = new Request.Builder()
                        .url(BASE_URL)
                        .post(requestBody)
                        // 模拟浏览器 UA，防止被拦截
                        .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                        .build();

                // 4. 发送请求并解析
                try (Response response = httpClient.newCall(request).execute()) {
                    if (response.isSuccessful()) {
                        ResponseBody body = response.body();
                        if (body == null) return results;

                        String jsonString = body.string();
                        JsonNode root = objectMapper.readTree(jsonString);

                        if (root != null && root.has("results")) {
                            for (JsonNode node : root.get("results")) {
                                JsonNode header = node.get("header");
                                JsonNode data = node.get("data");
                                // 兼容性处理：有些返回可能没有 similarity 字段或者格式不同
                                double similarity = 0.0;
                                if (header.has("similarity")) {
                                    similarity = Double.parseDouble(header.get("similarity").asText());
                                }

                                if (similarity < 60) continue;

                                String title = extractTitle(data);
                                String extUrl = "";
                                if (data.has("ext_urls")) {
                                    extUrl = data.get("ext_urls").get(0).asText();
                                }
                                String thumbnail = header.has("thumbnail") ? header.get("thumbnail").asText() : "";

                                results.add(new SearchResultItemDTO(
                                        title, extUrl, thumbnail, null, "saucenao",
                                        String.format("%.2f%%", similarity), "", ""
                                ));
                            }
                        }
                    } else {
                        log.error("SauceNAO API failed: HTTP {} - {}", response.code(), response.message());
                    }
                }

            } catch (Exception e) {
                log.error("SauceNAO search error", e);
            }
            return results;
        }, virtualThreadExecutor);
    }

    /**
     * 读取本地文件字节 (不再处理网络下载)
     */
    private byte[] getLocalImageBytes(String urlOrPath) {
        try {
            // 情况 A: 本地文件协议 (file:///D:/...)
            if (urlOrPath.startsWith("file:")) {
                URI uri = URI.create(urlOrPath);
                Path path = Path.of(uri);
                return Files.readAllBytes(path);
            }
            // 情况 B: 纯本地路径 (D:\test.jpg 或 /home/test.jpg)
            else {
                Path path = Paths.get(urlOrPath);
                if (Files.exists(path)) {
                    return Files.readAllBytes(path);
                }
            }
        } catch (IOException | IllegalArgumentException e) {
            log.error("Error reading local image bytes from: {}", urlOrPath, e);
        }
        return null;
    }

    private String extractTitle(JsonNode data) {
        if (data.has("title")) return data.get("title").asText();
        if (data.has("eng_name")) return data.get("eng_name").asText();
        if (data.has("jp_name")) return data.get("jp_name").asText(); // 增加 jp_name 提取
        if (data.has("material")) return data.get("material").asText();
        if (data.has("source")) return data.get("source").asText();
        // 如果是 Pixiv，尝试提取 member_name
        if (data.has("member_name")) return "Artist: " + data.get("member_name").asText();
        return "SauceNAO Result";
    }
}
