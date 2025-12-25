package com.github.winefoxbot.service.pixiv.impl;

import cn.hutool.core.util.URLUtil;
import com.github.winefoxbot.config.PixivConfig;
import com.github.winefoxbot.service.pixiv.PixivRankService;
import lombok.RequiredArgsConstructor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author FlanChan (badapple495@outlook.com)
 * @since 2025-12-10-21:37
 */
@Service
@RequiredArgsConstructor
public class PixivRankServiceImpl implements PixivRankService {
    private final OkHttpClient httpClient;
    private final PixivConfig pixivConfig;
    private final String baseUrl = "https://www.pixiv.net/ranking.php";
    /**
     * 获取排行榜 ID 列表（day / weekly / monthly）
     */
    @Override
    public List<String> getRank(PixivRankService.Mode mode, PixivRankService.Content content, boolean enabledR18) throws IOException {
        String queryParams = URLUtil.buildQuery(Map.of(
                "mode", mode.getValue() + (enabledR18 ? "_r18" : ""),
                "content", content.getValue()
        ), StandardCharsets.UTF_8);
        String url = baseUrl + "?" + queryParams;
        System.out.println(url);
        Request request = new Request.Builder()
                .url(url)
                .headers(pixivConfig.getHeaders())
                .build();
        try (Response resp = httpClient.newCall(request).execute()) {
            if (!resp.isSuccessful()) {
                throw new IOException("Pixiv 请求失败：" + resp.code());
            }
            String html = resp.body().string();
            return parseArtworkIds(html);
        }
    }

    /**
     * 从 HTML 中解析出所有 artworks ID
     */
    private List<String> parseArtworkIds(String html) {
        Document doc = Jsoup.parse(html);
        Elements links = doc.select("a[href*=/artworks/]");

        Set<String> ids = new LinkedHashSet<>();

        for (var a : links) {
            String href = a.attr("href");
            Matcher m = Pattern.compile("/artworks/(\\d+)").matcher(href);
            if (m.find()) {
                ids.add(m.group(1));
            }
        }
        return new ArrayList<>(ids);
    }
}