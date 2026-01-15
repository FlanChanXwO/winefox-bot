package com.github.winefoxbot.plugins.imgexploration.strategy.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.winefoxbot.plugins.imgexploration.model.dto.SearchResultItemDTO;
import com.github.winefoxbot.plugins.imgexploration.strategy.ImageSearchStrategy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

/**
 * @author FlanChan
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class YandexStrategy implements ImageSearchStrategy {

    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final ExecutorService virtualThreadExecutor;
    private final static String BASE_URL = "https://yandex.com/images/search";

    @Override
    public String getServiceName() {
        return "Yandex";
    }

    @Override
    public CompletableFuture<List<SearchResultItemDTO>> search(String imgUrl, String apiKey) {
        return CompletableFuture.supplyAsync(() -> {
            log.info("Yandex searching...");
            List<SearchResultItemDTO> results = new ArrayList<>();
            try {
                HttpUrl httpUrl = HttpUrl.parse(BASE_URL);
                HttpUrl.Builder builder = httpUrl.newBuilder();
                builder.addQueryParameter("rpt", "imageview");
                builder.addQueryParameter("url", URLEncoder.encode(imgUrl, StandardCharsets.UTF_8));

                String searchUrl = builder.build().toString();
                Request request = new Request.Builder()
                        .url(searchUrl)
                        .get()
                        .build();
                try (Response response = httpClient.newCall(request).execute()) {
                    if (response.isSuccessful()) {
                        ResponseBody body = response.body();
                        if (body == null) {
                            return results;
                        }

                        String html = body.string();
                        Document doc = Jsoup.parse(html);
                        Element element = doc.selectFirst(".cbir-section.cbir-section_name_sites > div");

                        if (element != null && element.hasAttr("data-state")) {
                            JsonNode root = objectMapper.readTree(element.attr("data-state"));
                            if (root.has("sites")) {
                                for (JsonNode site : root.get("sites")) {
                                    if (results.size() >= 5) break;
                                    String thumb = "https:" + site.get("thumb").get("url").asText();
                                    String url = site.get("url").asText();
                                    String title = site.get("title").asText();
                                    String desc = site.get("description").asText();
                                    String domain = site.get("domain").asText();

                                    results.add(new SearchResultItemDTO(title, url, thumb, null, "Yandex", null, desc, domain));
                                }
                            }
                        }
                    }
                }
            } catch (Exception e) {
                log.error("Yandex error", e);
            }
            return results;
        }, virtualThreadExecutor);
    }
}
