package com.github.winefoxbot.plugins.bittorrent.service.impl;

import cn.hutool.core.util.StrUtil;
import com.github.winefoxbot.plugins.bittorrent.config.BitTorrentConfig;
import com.github.winefoxbot.plugins.bittorrent.model.dto.BitTorrentMagnetInfo;
import com.github.winefoxbot.plugins.bittorrent.model.dto.BitTorrentPageInfo;
import com.github.winefoxbot.plugins.bittorrent.model.dto.BitTorrentSearchResult;
import com.github.winefoxbot.plugins.bittorrent.model.dto.BitTorrentSearchResultItem;
import com.github.winefoxbot.plugins.bittorrent.service.BitTorrentService;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.WaitUntilState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.unit.DataSize;

import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Slf4j
public class BitTorrentServiceImpl implements BitTorrentService {

    private final BitTorrentConfig bitTorrentConfig;
    private final OkHttpClient client;
    private final Browser browser;

    @Override
    public BitTorrentSearchResult search(String keyword, int page) {
        return search(keyword, page, false, null);
    }

    public String getRedirectedUrl() {
        try (Page page = browser.newPage()) {
            page.navigate(bitTorrentConfig.getBaseUrl(), new Page.NavigateOptions().
                    setWaitUntil(WaitUntilState.NETWORKIDLE).
                    setTimeout(30000)
            );
            return page.url();
        }
    }


    private BitTorrentSearchResult search(String keyword, int page, boolean nextPage, String nextUrl) {
        if (StrUtil.isBlank(keyword)) {
            throw new IllegalArgumentException("搜索关键词不能为空");
        }
        log.info("开始搜索关键词: {} , 页码: {} , nextPage: {} , nextUrl: {}", keyword, page, nextPage, nextUrl);

        try {
            Request request = buildSearchRequest(keyword, nextPage, nextUrl);
            try (Response response = client.newCall(request).execute()) {
                // 如果遇到403禁止访问，说明可能域名已更新，触发重定向探测逻辑
                if (response.code() == HttpStatus.FORBIDDEN.value()) {
                    log.warn("访问被禁止(403)，尝试通过访问根域名来探测并更新 baseUrl...");
                    String redirectedUrl = getRedirectedUrl().replaceAll("(https://[^/]+).*", "$1");
                    log.info("探测到新的 baseUrl: {}", redirectedUrl);
                    // 更新成员变量
                    bitTorrentConfig.setBaseUrl(redirectedUrl);
                    bitTorrentConfig.setReferer(redirectedUrl + "/");
                    bitTorrentConfig.setOrigin(redirectedUrl);
                    return search(keyword, page, nextPage, nextUrl);
                }

                // 如果页数大于1，且不是通过 nextUrl 访问的，说明需要拼接分页URL
                if (page > 1 && !nextPage) {
                    String redirectedUrl = response.request().url().toString();
                    int lastDotIndex = redirectedUrl.lastIndexOf(".");
                    int lastUnderscoreIndex = redirectedUrl.lastIndexOf("_", lastDotIndex);
                    // 确保索引有效
                    if (lastUnderscoreIndex != -1 && lastDotIndex > lastUnderscoreIndex) {
                        String nextPageUrl = redirectedUrl.substring(0, lastUnderscoreIndex + 1) + page + redirectedUrl.substring(lastDotIndex);
                        log.info("生成下一页URL: {}", nextPageUrl);
                        // 递归调用，nextPage设为true以避免无限递归
                        return search(keyword, page, true, nextPageUrl);
                    }
                }

                String html = response.body().string();
                return parseSearchResult(html);
            }
        } catch (Exception e) {
            log.error("搜索失败: {}", e.getMessage(), e);
            return null;
        }
    }


    /**
     * 根据参数构建搜索请求。
     */
    private Request buildSearchRequest(String keyword, boolean nextPage, String nextUrl) {
        String url = nextPage ? nextUrl : bitTorrentConfig.getBaseUrl() + "/search?kw=" + keyword;
        return new Request.Builder().url(url)
                .addHeader(HttpHeaders.REFERER, bitTorrentConfig.getReferer())
                .addHeader(HttpHeaders.ORIGIN, bitTorrentConfig.getOrigin())
                .addHeader(HttpHeaders.USER_AGENT, "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/143.0.0.0 Safari/537.36 Edg/143.0.0.0")
                .build();
    }

    /**
     * 解析搜索结果的 HTML 页面。
     */
    private BitTorrentSearchResult parseSearchResult(String html) {
        BitTorrentSearchResult searchResult = new BitTorrentSearchResult();
        List<BitTorrentSearchResultItem> results = new ArrayList<>();
        Document doc = Jsoup.parse(html);
        Elements divElements = doc.select("div.col-md-8");

        if (divElements.isEmpty()) {
            log.error("未找到搜索结果页面的主要内容区域, HTML: {}", html);
            return null;
        }

        for (Element divElement : divElements) {
            List<Map<String, String>> items = parseItems(divElement);
            for (Map<String, String> item : items) {
                if (results.size() >= bitTorrentConfig.getMaxSearchSize()) break;

                BitTorrentSearchResultItem resultItem = new BitTorrentSearchResultItem();
                String link = item.get("link");
                if (link != null) {
                    resultItem.setMagnetInfo(getMagnet(link));
                }
                resultItem.setTitle(item.get("title"));

                String sizeStr = item.get("size");
                if (sizeStr != null) {
                    resultItem.setSize(parseSize(sizeStr));
                }

                String dateStr = item.get("date");
                if (dateStr != null) {
                    LocalDate date = LocalDate.parse(dateStr, DateTimeFormatter.ofPattern("yyyy-MM-dd"));
                    resultItem.setDate(date.atStartOfDay());
                }

                String hotStr = item.get("hot");
                if (hotStr != null) {
                    resultItem.setHot(Integer.parseInt(hotStr.replace("℃", "").trim()));
                }
                results.add(resultItem);
            }
            if (results.size() >= bitTorrentConfig.getMaxSearchSize()) break;
        }

        if (!results.isEmpty()) {
            searchResult.setItems(results);
            searchResult.setPageInfo(parsePagination(doc));
        }
        return searchResult;
    }

    /**
     * 解析文件大小字符串。
     */
    private DataSize parseSize(String sizeStr) {
        Pattern pattern = Pattern.compile("(\\d+(?:\\.\\d+)?)\\s*([KMGTP]?B)", Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(sizeStr.trim());
        if (matcher.matches()) {
            double number = Double.parseDouble(matcher.group(1));
            String unit = matcher.group(2).toUpperCase();
            long bytes = switch (unit) {
                case "KB" -> (long) (number * 1024);
                case "MB" -> (long) (number * 1024 * 1024);
                case "GB" -> (long) (number * 1024 * 1024 * 1024);
                case "TB" -> (long) (number * 1024L * 1024L * 1024L * 1024L);
                default -> (long) number; // B
            };
            return DataSize.ofBytes(bytes);
        } else {
            log.warn("无法解析的大小字符串: {}", sizeStr);
            return null;
        }
    }


    private BitTorrentMagnetInfo getMagnet(String uri) {
        try {
            Request request = new Request.Builder().url(bitTorrentConfig.getBaseUrl() + uri)
                    .addHeader(HttpHeaders.USER_AGENT, "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/143.0.0.0 Safari/537.36 Edg/143.0.0.0")
                    .build();
            try (Response response = client.newCall(request).execute()) {
                if (!response.isSuccessful() || response.body() == null) {
                    log.error("获取磁力链 " + uri + " 失败, 状态码: {}", response.code());
                    return null;
                }
                String html = response.body().string();
                Document doc = Jsoup.parse(html);
                Element magnetTextarea = doc.selectFirst("textarea#MagnetLink");
                String magnet = magnetTextarea != null ? magnetTextarea.text() : "/";

                List<String> fileList = new ArrayList<>();
                Elements fileRows = doc.select("tbody > tr");
                for (Element row : fileRows) {
                    Element tdElement = row.selectFirst("td");
                    if (tdElement != null) {
                        fileList.add(tdElement.text());
                    }
                }

                BitTorrentMagnetInfo bitTorrentMagnetInfo = new BitTorrentMagnetInfo();
                bitTorrentMagnetInfo.setFiles(fileList);
                bitTorrentMagnetInfo.setMagnet(magnet);
                return bitTorrentMagnetInfo;
            }
        } catch (IOException e) {
            log.error("获取磁力链 " + uri + " 失败: {}", e.getMessage(), e);
            return null;
        }
    }

    private List<Map<String, String>> parseItems(Element divElement) {
        List<Map<String, String>> resultList = new ArrayList<>();
        Elements panels = divElement.select("div.panel.panel-default");
        for (Element panel : panels) {
            Map<String, String> data = new HashMap<>();
            Element aTag = panel.selectFirst("h5.item-title a.pb");
            if (aTag != null) {
                data.put("title", aTag.text());
                data.put("link", aTag.attr("href"));
            }
            Elements tds = panel.select("table td span.label.label-info b");
            if (tds.size() >= 3) {
                data.put("date", tds.get(0).text());
                data.put("size", tds.get(1).text());
                data.put("hot", tds.get(2).text());
            }
            resultList.add(data);
        }
        return resultList;
    }

    public BitTorrentPageInfo parsePagination(Element divElement) {
        BitTorrentPageInfo pageInfo = new BitTorrentPageInfo();
        Element pagination = divElement.selectFirst("ul.pagination");
        if (pagination != null) {
            Elements items = pagination.select("li");
            for (Element li : items) {
                String text = li.text().trim();
                if (text.matches("\\d+")) {
                    pageInfo.pages.add(Integer.parseInt(text));
                } else if (text.equals("下页")) {
                    pageInfo.hasNextPage = true;
                }
            }
        }
        return pageInfo;
    }
}
