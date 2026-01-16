package com.github.winefoxbot.plugins.imgexploration.strategy.impl;

import com.github.winefoxbot.plugins.imgexploration.config.ImgExplorationConfig;
import com.github.winefoxbot.plugins.imgexploration.model.dto.SearchResultItemDTO;
import com.github.winefoxbot.plugins.imgexploration.strategy.ImageSearchStrategy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.eclipse.sisu.PostConstruct;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

/**
 * Ascii2D 搜图策略实现
 * 模式：自动根据输入切换
 * 1. HTTP链接 -> /search/uri 接口
 * 2. 本地文件 -> /search/file 接口
 * @author FlanChan
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class Ascii2dSearchStrategy implements ImageSearchStrategy {

    private final ExecutorService executorService;
    private final OkHttpClient httpClient;
    private final ImgExplorationConfig imgExplorationConfig;

    private static final String BASE_URL = "https://ascii2d.net";
    private static final String SEARCH_FILE_URL = BASE_URL + "/search/file";
    private static final String SEARCH_URI_URL = BASE_URL + "/search/uri";
    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36";
    private String cookie = "_session_id=%s";

    @PostConstruct
    public void init() {
        cookie = cookie.formatted(imgExplorationConfig.getAscii2dSessionId());
    }

    @Override
    public String getServiceName() {
        return "ascii2d";
    }

    @Override
    public CompletableFuture<List<SearchResultItemDTO>> search(String inputUrl) {
        log.info("ascii2d searching... input: {}", inputUrl);

        int shNum = 2;
        int tzNum = 3;

        // 步骤 1: 获取 Token 并根据 URL 类型获取 Result URL
        return CompletableFuture.supplyAsync(() -> {
                    String token = fetchAuthenticityToken();
                    if (token == null) return null;

                    // 分流处理：网络链接 vs 本地文件
                    if (inputUrl.startsWith("http://") || inputUrl.startsWith("https://")) {
                        return postUrlAndGetResultUrl(inputUrl, token);
                    } else {
                        byte[] imageBytes = resolveLocalImageBytes(inputUrl);
                        if (imageBytes.length == 0) return null;
                        return uploadImageAndGetResultUrl(imageBytes, token);
                    }
                }, executorService)
                // 步骤 2: 获取到 Result URL 后，并行爬取结果
                .thenCompose(resultUrl -> {
                    if (resultUrl == null) {
                        // 如果第一步失败，返回空的 Context，保证链式调用不中断
                        return CompletableFuture.completedFuture(new ProcessingContext(Collections.emptyList(), Collections.emptyList()));
                    }

                    var colorFuture = CompletableFuture.supplyAsync(() -> fetchAndParseResultPage(resultUrl, false), executorService);
                    var bovwFuture = CompletableFuture.supplyAsync(() -> fetchAndParseResultPage(resultUrl, true), executorService);

                    // 合并 Color 和 Bovw 结果
                    return colorFuture.thenCombine(bovwFuture, (colorResults, bovwResults) -> {
                        List<Ascii2dRawItem> combinedRaw = new ArrayList<>();
                        combinedRaw.addAll(bovwResults.stream().limit(tzNum).toList());
                        combinedRaw.addAll(colorResults.stream().limit(shNum).toList());

                        List<SearchResultItemDTO> resultLi = new ArrayList<>();
                        List<String> thumbnailUrls = new ArrayList<>();

                        for (Ascii2dRawItem single : combinedRaw) {
                            String externalUrl = getExternalUrlFromHtml(single.originHtml());
                            String finalUrl = (single.url() != null && !single.url().isEmpty()) ? single.url() : externalUrl;

                            if (finalUrl == null) continue;

                            try {
                                finalUrl = URLDecoder.decode(finalUrl, StandardCharsets.UTF_8);
                            } catch (Exception e) { /* ignore */ }

                            resultLi.add(new SearchResultItemDTO(
                                    single.title(), finalUrl, single.thumbnail(), null, "ascii2d"
                            ));
                            thumbnailUrls.add(single.thumbnail());
                        }
                        return new ProcessingContext(resultLi, thumbnailUrls);
                    });
                })
                // 步骤 3: 下载缩略图 (保持原逻辑)
                .thenCompose(context -> {
                    if (context.results().isEmpty()) {
                        return CompletableFuture.completedFuture(Collections.<SearchResultItemDTO>emptyList());
                    }

                    return downloadImagesBatch(context.thumbnailUrls()).thenApply(imagesBytes -> {
                        List<SearchResultItemDTO> finalResults = new ArrayList<>();
                        for (int i = 0; i < context.results().size(); i++) {
                            SearchResultItemDTO original = context.results().get(i);
                            byte[] imgData = (i < imagesBytes.size()) ? imagesBytes.get(i) : null;
                            finalResults.add(new SearchResultItemDTO(
                                    original.title(), original.url(), original.thumbnail(),
                                    imgData, original.source(), null, null, null
                            ));
                        }
                        return finalResults;
                    });
                })
                .exceptionally(e -> {
                    log.error("Ascii2d search error", e);
                    return Collections.emptyList();
                });
    }

    /**
     * 新增：处理 HTTP 链接，直接调用 /search/uri 接口
     */
    private String postUrlAndGetResultUrl(String imageUrl, String token) {
        RequestBody requestBody = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("utf8", "✓")
                .addFormDataPart("authenticity_token", token)
                .addFormDataPart("uri", imageUrl) // 关键参数 uri
                .addFormDataPart("search", "")     // 关键参数 search (空值即可，但通常需要传)
                .build();

        Request request = new Request.Builder()
                .url(SEARCH_URI_URL)
                .post(requestBody)
                .header("User-Agent", USER_AGENT)
                .header("Cookie", cookie)
                .build();

        return executeSearchRequest(request);
    }

    /**
     * 原有：处理本地文件，调用 /search/file 接口
     */
    private String uploadImageAndGetResultUrl(byte[] imageBytes, String token) {
        RequestBody requestBody = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("utf8", "✓")
                .addFormDataPart("authenticity_token", token)
                .addFormDataPart("file", "image.jpg",
                        RequestBody.create(imageBytes, MediaType.parse("image/jpeg")))
                .build();

        Request request = new Request.Builder()
                .url(SEARCH_FILE_URL)
                .post(requestBody)
                .header("User-Agent", USER_AGENT)
                .header("Cookie", cookie)
                .build();

        return executeSearchRequest(request);
    }

    /**
     * 抽取公共的请求执行逻辑
     */
    private String executeSearchRequest(Request request) {
        try (Response response = httpClient.newCall(request).execute()) {
            // Ascii2d 搜索成功后会 302 重定向到结果页
            // OkHttp 默认会自动处理重定向，所以最终的 response.request().url() 就是结果页 URL
            if (response.isSuccessful()) {
                String finalUrl = response.request().url().toString();
                // 简单的校验，确保跳到了 color 或 bovw 页面
                if (finalUrl.contains("/search/color/") || finalUrl.contains("/search/bovw/")) {
                    return finalUrl;
                }
                // 某些情况下可能还在 loading 或出错
                log.warn("Ascii2d redirect unexpected url: {}", finalUrl);
                return finalUrl; // 尝试继续
            } else {
                log.warn("Ascii2d search request failed: code={}", response.code());
            }
        } catch (IOException e) {
            log.error("Network error during ascii2d search request", e);
        }
        return null;
    }

    /**
     * 仅处理本地文件的读取 (重命名自 resolveImageBytes 并移除了 http 下载逻辑)
     */
    private byte[] resolveLocalImageBytes(String inputUrl) {
        if (inputUrl == null || inputUrl.isBlank()) return new byte[0];
        try {
            if (inputUrl.startsWith("file:")) {
                Path path = Paths.get(URI.create(inputUrl));
                return Files.readAllBytes(path);
            } else {
                Path path = Paths.get(inputUrl);
                if (Files.exists(path)) {
                    return Files.readAllBytes(path);
                }
            }
        } catch (IOException | IllegalArgumentException e) {
            log.error("Failed to read local image bytes from: " + inputUrl, e);
        }
        return new byte[0];
    }

    // --- 其他辅助方法保持不变 (fetchAuthenticityToken, fetchAndParseResultPage 等) ---

    private String fetchAuthenticityToken() {
        Request request = new Request.Builder()
                .url(BASE_URL)
                .header("User-Agent", USER_AGENT)
                .header("Cookie", cookie)
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (response.isSuccessful() && response.body() != null) {
                String html = response.body().string();
                Document doc = Jsoup.parse(html);
                Element tokenInput = doc.selectFirst("input[name=authenticity_token]");
                if (tokenInput != null) {
                    return tokenInput.attr("value");
                }
            }
        } catch (IOException e) {
            log.error("Failed to fetch ascii2d home page for token", e);
        }
        return null;
    }

    private List<Ascii2dRawItem> fetchAndParseResultPage(String baseUrl, boolean bovw) {
        List<Ascii2dRawItem> results = new ArrayList<>();
        String targetUrl = baseUrl;

        if (bovw) {
            targetUrl = baseUrl.replace("/color/", "/bovw/");
        } else if (!targetUrl.contains("/color/")) {
            targetUrl = targetUrl.replace("/bovw/", "/color/");
        }

        Request request = new Request.Builder()
                .url(targetUrl)
                .header("User-Agent", USER_AGENT)
                .header("Cookie", cookie)
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) return results;
            return parseAscii2dHtml(response.body().string());
        } catch (IOException e) {
            log.error("Error fetching result page: " + targetUrl, e);
        }
        return results;
    }

    private List<Ascii2dRawItem> parseAscii2dHtml(String htmlBody) {
        List<Ascii2dRawItem> results = new ArrayList<>();
        Document doc = Jsoup.parse(htmlBody);
        Elements itemBoxes = doc.select(".item-box");

        for (int i = 1; i < itemBoxes.size(); i++) {
            Element box = itemBoxes.get(i);
            try {
                Element imgElement = box.selectFirst("img");
                String thumbnail = imgElement != null ? BASE_URL + imgElement.attr("src") : "";

                Element detailBox = box.selectFirst(".detail-box");
                if (detailBox == null) continue;

                Elements links = detailBox.select("h6 a");
                String title = "";
                String url = null;
                if (!links.isEmpty()) {
                    Element titleLink = links.first();
                    title = titleLink.text();
                    url = titleLink.attr("href");
                }
                String originHtml = detailBox.html();
                results.add(new Ascii2dRawItem(title, thumbnail, url, originHtml));
            } catch (Exception e) {
                log.debug("Failed to parse an ascii2d item", e);
            }
        }
        return results;
    }

    private String getExternalUrlFromHtml(String htmlFragment) {
        if (htmlFragment == null || htmlFragment.isEmpty()) return null;
        Document doc = Jsoup.parseBodyFragment(htmlFragment);
        Element externalLink = doc.selectFirst("div.external > a");
        if (externalLink != null) {
            return externalLink.attr("href");
        }
        return null;
    }

    private CompletableFuture<List<byte[]>> downloadImagesBatch(List<String> urls) {
        var futures = urls.stream()
                .map(url -> CompletableFuture.supplyAsync(() -> downloadSingleImage(url), executorService))
                .toList();

        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .thenApply(v -> futures.stream()
                        .map(CompletableFuture::join)
                        .toList());
    }

    private byte[] downloadSingleImage(String url) {
        if (url == null || url.isEmpty()) return new byte[0];
        Request request = new Request.Builder()
                .url(url)
                .header("User-Agent", USER_AGENT)
                .build();
        try (Response response = httpClient.newCall(request).execute()) {
            if (response.isSuccessful() && response.body() != null) {
                return response.body().bytes();
            }
        } catch (IOException e) {
            log.warn("Failed to download image: {}", url);
        }
        return new byte[0];
    }

    private record Ascii2dRawItem(String title, String thumbnail, String url, String originHtml) {}
    private record ProcessingContext(List<SearchResultItemDTO> results, List<String> thumbnailUrls) {}
}
