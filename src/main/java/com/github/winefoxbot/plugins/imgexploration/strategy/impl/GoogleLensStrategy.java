package com.github.winefoxbot.plugins.imgexploration.strategy.impl;

import com.github.winefoxbot.plugins.imgexploration.model.dto.SearchResultItemDTO;
import com.github.winefoxbot.plugins.imgexploration.strategy.ImageSearchStrategy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
@RequiredArgsConstructor
public class GoogleLensStrategy implements ImageSearchStrategy {

    private final ExecutorService virtualThreadExecutor;
    private final OkHttpClient httpClient;

    // 预编译正则，提高性能
    private static final Pattern ID_PATTERN = Pattern.compile("\\['(.*?)'\\]");
    private static final Pattern BASE64_PATTERN = Pattern.compile("data:image/jpeg;base64,(.*?)'");

    @Override
    public String getServiceName() {
        return "Google";
    }

    @Override
    public CompletableFuture<List<SearchResultItemDTO>> search(String imgUrl, String apiKey) {
        return CompletableFuture.supplyAsync(() -> {
            List<SearchResultItemDTO> resultList = new ArrayList<>();
            // 创建一个不自动跳转的 Client，模拟 Python 的手动处理 Redirect
            OkHttpClient noRedirectClient = httpClient.newBuilder()
                    .proxySelector(httpClient.proxySelector())
                    .followRedirects(false)
                    .followSslRedirects(false)
                    .build();

            // 简单的内存 Cookie 存储
            Map<String, String> cookies = new HashMap<>();

            try {
                log.info("Google Lens searching for: {}", imgUrl);

                // --- Step 1: Upload by URL ---
                HttpUrl initialUrl = HttpUrl.parse("https://lens.google.com/uploadbyurl").newBuilder()
                        .addQueryParameter("url", imgUrl)
                        .build();

                Request request1 = buildRequest(initialUrl.toString(), null, cookies);

                String redirectUrl;
                try (Response response1 = noRedirectClient.newCall(request1).execute()) {
                    updateCookies(response1, cookies);
                    // Python logic: self.__google_cookies.update(google_lens.headers)
                    // redirect_url = google_lens.headers.get("location")
                    redirectUrl = response1.header("Location");

                    if (redirectUrl == null) {
                        log.warn("Google Lens did not return a redirect location.");
                        return resultList;
                    }
                }

                // --- Step 2: Follow Redirect ---
                // Python logic: header["referer"] = str(google_lens.url)
                Request request2 = buildRequest(redirectUrl, initialUrl.toString(), cookies);
                String mainPageHtml;
                try (Response response2 = noRedirectClient.newCall(request2).execute()) {
                    updateCookies(response2, cookies);
                    if (response2.body() == null) return resultList;
                    mainPageHtml = response2.body().string();
                }

                // --- Step 3: Parse Main Page & Find "Visual Matches" Link ---
                Document mainDoc = Jsoup.parse(mainPageHtml);
                // Python XPath: //span[text()='查看完全匹配的结果']/ancestor::a[1]
                // Jsoup 对应写法:
                Element matchLink = mainDoc.select("span:containsOwn(查看完全匹配的结果)").first();

                if (matchLink != null) {
                    Element anchor = matchLink.closest("a");
                    if (anchor != null) {
                        String href = anchor.attr("href");
                        String fullMatchUrl = "https://www.google.com" + href;

                        // --- Step 4: Fetch Full Match Page ---
                        Request request3 = buildRequest(fullMatchUrl, null, cookies);
                        String fullMatchHtml;
                        try (Response response3 = noRedirectClient.newCall(request3).execute()) {
                            if (response3.body() == null) return resultList;
                            fullMatchHtml = response3.body().string();
                        }

                        // --- Step 5: Extract Images and Data ---
                        Document fullMatchDoc = Jsoup.parse(fullMatchHtml);

                        // 解析 Script 中的 Base64 图片映射
                        Map<String, String> idBase64Mapping = parseBase64Image(fullMatchDoc);

                        // Python XPath: //div[@id='search']/div/div/div
                        Elements resItems = fullMatchDoc.select("#search > div > div > div");

                        for (Element item : resItems) {
                            try {
                                Element aTag = item.selectFirst("a");
                                if (aTag == null) continue;

                                String link = aTag.attr("href");

                                Element imgTag = aTag.selectFirst("img");
                                if (imgTag == null) continue;
                                String imgId = imgTag.id();

                                // Title XPath: .//a/div/div[2]/div[1]/text()
                                // Jsoup 结构化选择可能稍微不同，这里尝试通过 CSS 选择器定位
                                // 通常结构是 a -> div -> div -> div(title)
                                String title = "";
                                Element titleDiv = aTag.selectFirst("div > div:nth-child(2) > div:nth-child(1)");
                                if (titleDiv != null) {
                                    title = titleDiv.text();
                                }

                                String imgBase64 = idBase64Mapping.get(imgId);
                                byte[] imgBytes = null;
                                if (imgBase64 != null) {
                                    imgBytes = Base64.getDecoder().decode(imgBase64);
                                }

                                if (imgBytes != null) {
                                    // 假设 SearchResultItemDTO 是一个 Record 或 DTO
                                    // 这里适配你之前的 SearchResultItemDTO 结构
                                    SearchResultItemDTO resultItem = new SearchResultItemDTO(
                                            title,
                                            link,
                                            "", // thumbnail url 为空，因为我们直接拿到了 bytes
                                            imgBytes,
                                            "Google",
                                            "", // similarity
                                            "", // description
                                            ""  // domain
                                    );
                                    resultList.add(resultItem);
                                }
                            } catch (Exception e) {
                                log.warn("Error parsing individual Google Lens item", e);
                            }
                        }
                    }
                }

                log.info("Google result count: {}", resultList.size());
                return resultList;

            } catch (Exception e) {
                log.error("Google Lens search failed", e);
                // 如有必要，可在此处将错误页面写入文件以便调试，类似于 Python 代码中的 open("Googlelens_error_page.html"...)
                return new ArrayList<>();
            }
        }, virtualThreadExecutor);
    }

    /**
     * 构建包含特定 Header 的请求
     */
    private Request buildRequest(String url, String referer, Map<String, String> cookies) {
        Request.Builder builder = new Request.Builder()
                .url(url)
                .header("accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.7")
                .header("accept-encoding", "gzip, deflate, br, zstd") // OkHttp 默认会自动处理 gzip，但加上也没事
                .header("accept-language", "zh-CN,zh-HK;q=0.9,zh;q=0.8,en-US;q=0.7,en;q=0.6") // 关键：必须是中文，否则 Step 3 的文本匹配会失败
                .header("cache-control", "no-cache")
                .header("user-agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36");

        if (referer != null) {
            builder.header("referer", referer);
        }

        if (!cookies.isEmpty()) {
            StringBuilder cookieHeader = new StringBuilder();
            for (Map.Entry<String, String> entry : cookies.entrySet()) {
                if (cookieHeader.length() > 0) cookieHeader.append("; ");
                cookieHeader.append(entry.getKey()).append("=").append(entry.getValue());
            }
            builder.header("Cookie", cookieHeader.toString());
        }

        return builder.build();
    }

    /**
     * 从响应头更新 Cookie Map
     */
    private void updateCookies(Response response, Map<String, String> cookies) {
        List<String> setCookies = response.headers("Set-Cookie");
        for (String cookieStr : setCookies) {
            // 简单的 Cookie 解析逻辑
            String[] parts = cookieStr.split(";", 2);
            String[] nameValue = parts[0].split("=", 2);
            if (nameValue.length == 2) {
                cookies.put(nameValue[0].trim(), nameValue[1].trim());
            }
        }
    }

    /**
     * 从 HTML 文档中解析 id 和对应的图片 base64
     * 对应 Python: parseBase64Image(document: etree.Element)
     */
    private Map<String, String> parseBase64Image(Document document) {
        Map<String, String> resDic = new HashMap<>();
        Elements scripts = document.select("script[nonce]");

        for (Element script : scripts) {
            String funcText = script.html();
            if (funcText.isEmpty()) continue;

            Matcher idMatch = ID_PATTERN.matcher(funcText);
            String id = idMatch.find() ? idMatch.group(1) : null;

            Matcher base64Match = BASE64_PATTERN.matcher(funcText);
            String b64 = base64Match.find() ? base64Match.group(1) : null;

            if (b64 != null) {
                // Python: replace(r"\x3d", "=")
                b64 = b64.replace("\\x3d", "=");
            }

            if (id != null && b64 != null) {
                if (id.contains("','")) {
                    for (String dimg : id.split("','")) {
                        resDic.put(dimg, b64);
                    }
                } else {
                    resDic.put(id, b64);
                }
            }
        }
        return resDic;
    }
}
