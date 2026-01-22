package com.github.winefoxbot.plugins.dailyreport.service;


import cn.hutool.core.util.RandomUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.winefoxbot.core.config.playwright.PlaywrightConfig;
import com.github.winefoxbot.core.service.file.FileStorageService;
import com.github.winefoxbot.core.utils.DynamicResourceLoader;
import com.github.winefoxbot.plugins.dailyreport.config.DailyReportProperties;
import com.github.winefoxbot.plugins.dailyreport.model.dto.BiliHotwordDTO;
import com.github.winefoxbot.plugins.dailyreport.model.dto.HitokotoDTO;
import com.github.winefoxbot.plugins.dailyreport.model.dto.NewsDataDTO;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.ScreenshotType;
import com.microsoft.playwright.options.WaitForSelectorState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpHeaders;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

/**
 * @author FlanChan
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DailyReportService {

    private static final String DATE_FORMAT_PATTERN = "yyyy-MM-dd";
    private static final String DAILY_REPORT_CACHE_DIR = "daily-report";
    private static final String HITOKOTO_API_URL = "https://v1.hitokoto.cn/?c=a&c=b&c=c&encode=json";
    private static final String BILI_HOTWORD_API_URL = "https://s.search.bilibili.com/main/hotword";
    private static final String IT_RSS_URL = "https://www.ithome.com/rss/";
    private static final String ANIME_API_URL = "https://api.bgm.tv/calendar";

    // 定义模板变量 Key
    private static final String CONTEXT_VARIABLE_ANIME_LIST = "animeList";
    private static final String CONTEXT_VARIABLE_ITNEWS_LIST = "itNewsList";
    private static final String CONTEXT_VARIABLE_60SNEWS_LIST = "newsList";
    private static final String CONTEXT_VARIABLE_HISTORY_LIST = "historyList";
    private static final String CONTEXT_VARIABLE_HITOKOTO = "hitokoto";
    private static final String CONTEXT_VARIABLE_HOLIDAY_LIST = "holidayList";
    private static final String CONTEXT_VARIABLE_NEWS_DATA = "newsData";
    private static final String CONTEXT_VARIABLE_BILI_HOTWORDS = "biliHotwords";
    private static final String CONTEXT_VARIABLE_CSS_STYLE = "cssStyle";
    private static final String [] CHARACTER_IMAGES = {
            "templates/winefox_daily_report/res/image/character_1.png",
            "templates/winefox_daily_report/res/image/character_2.png",
            "templates/winefox_daily_report/res/image/character_3.png"
    };

    private static final String PAGE_CONTAINER_SELECTOR = ".wrapper";
    private static final int BILI_HOTWORDS_LIMIT = 10;


    private record AnimeItem(String name, String image) {}

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern(DATE_FORMAT_PATTERN);

    private final OkHttpClient httpClient;
    private final Browser browser;
    private final PlaywrightConfig playwrightConfig;
    private final ObjectMapper objectMapper;
    private final TemplateEngine templateEngine;
    private final DailyReportProperties properties;
    private final FileStorageService fileStorageService;
    private final HolidayService holidayService;
    private final ExecutorService virtualThreadExecutor = Executors.newVirtualThreadPerTaskExecutor();
    private final Lock lock = new ReentrantLock();


    @Lazy
    @Autowired
    private DailyReportService self;

    /**
     * 获取日报图片。优先从缓存读取，否则生成新的图片。
     *
     * @return 日报图片的字节数组
     * @throws IOException 如果文件读写或网络请求失败
     */
    public byte[] getDailyReportImage() throws IOException {
        String cacheKey = getCachePathForToday();
        Path cachePath = fileStorageService.getFilePathByCacheKey(cacheKey);

        if (cachePath != null && Files.exists(cachePath)) {
            log.info("Serving daily report from cache: {}", cachePath);
            return Files.readAllBytes(cachePath);
        }

        lock.lock();
        try {
            if (cachePath != null && Files.exists(cachePath)) {
                log.info("Serving daily report from cache (double-checked): {}", cachePath);
                return Files.readAllBytes(cachePath);
            }
            return generateAndCacheReport(cacheKey);
        } finally {
            lock.unlock();
        }
    }


    /**
     * 定时任务：每天预生成当天的日报图片并缓存。
     */
    @Scheduled(cron = "${winefoxbot.plugins.dailyreport.pre-generate-cron:0 30 8 * * ?}")
    public void scheduledPreGenerateDailyReport() {
        try {
            log.info("Scheduled task: Pre-generating daily report image for today.");
            self.regenerateDailyReportImage();
            log.info("Scheduled task: Daily report image pre-generation completed.");
        } catch (IOException e) {
            log.error("Scheduled task: Failed to pre-generate daily report image.", e);
        }
    }


    /**
     * 强制重新生成当天的日报图片。
     *
     * @throws IOException 如果文件读写或网络请求失败
     */
    public void regenerateDailyReportImage() throws IOException {
        String cacheKey = getCachePathForToday();
        fileStorageService.deleteFile(cacheKey, null);
        generateAndCacheReport(cacheKey);
    }

    private byte[] generateAndCacheReport(String cacheKey) {
        log.info("Generating new daily report for {}", LocalDate.now());
        byte[] imageBytes = generateReportImage();

        CompletableFuture.runAsync(() -> {
            fileStorageService.saveFileByCacheKey(cacheKey, imageBytes, Duration.ofDays(1));
            log.info("Daily report cached successfully");
        }, virtualThreadExecutor);

        return imageBytes;
    }

    private byte[] generateReportImage() {
        // 1. 获取原有数据
        CompletableFuture<HitokotoDTO> hitokotoFuture = fetchData(HITOKOTO_API_URL, HitokotoDTO.class);
        CompletableFuture<NewsDataDTO> newsFuture = fetchNewsData();
        CompletableFuture<BiliHotwordDTO> biliHotwordFuture = fetchData(BILI_HOTWORD_API_URL, BiliHotwordDTO.class);

        // 2. 获取 IT 资讯 (XML) 和 动漫新番 (JSON)
        CompletableFuture<List<String>> itNewsFuture = fetchItNewsData();
        CompletableFuture<List<AnimeItem>> animeFuture = fetchAnimeData();

        // 等待所有任务完成
        CompletableFuture.allOf(hitokotoFuture, newsFuture, biliHotwordFuture, itNewsFuture, animeFuture).join();

        try {
            Map<String, Object> data = new HashMap<>();

            // 填充 Hitokoto
            data.put(CONTEXT_VARIABLE_HITOKOTO, hitokotoFuture.get());

            // 填充 60s 新闻 (注意这里对应 HTML 的 ${newsList})
            // NewsDataDTO.data().news() 返回的是 List<String>
            NewsDataDTO newsDto = newsFuture.get();
            data.put(CONTEXT_VARIABLE_60SNEWS_LIST, newsDto != null && newsDto.data() != null ? newsDto.data().news() : Collections.emptyList());
            // 复用 newsData 对象给 header 的日期使用 (HTML中用到了 newsData.date)
            data.put(CONTEXT_VARIABLE_NEWS_DATA, newsDto != null ? newsDto.data() : null);
            // 填充节假日信息
            data.put(CONTEXT_VARIABLE_HOLIDAY_LIST, holidayService.getHolidaysSorted());
            // 填充 B站热搜
            List<BiliHotwordDTO.HotwordItem> hotwords = biliHotwordFuture.get() != null
                    ? biliHotwordFuture.get().list().stream().limit(BILI_HOTWORDS_LIMIT).collect(Collectors.toList())
                    : Collections.emptyList();
            data.put(CONTEXT_VARIABLE_BILI_HOTWORDS, hotwords);
            // 填充 IT 资讯
            data.put(CONTEXT_VARIABLE_ITNEWS_LIST, itNewsFuture.get());
            // 填充 动漫数据
            data.put(CONTEXT_VARIABLE_ANIME_LIST, animeFuture.get());
            // 填充 历史上的今天 (Python源无此数据，置空)
            data.put(CONTEXT_VARIABLE_HISTORY_LIST, Collections.emptyList());
            log.info("All data fetched successfully, rendering image...");
            return renderHtmlToImage(data);
        } catch (Exception e) {
            log.error("Error occurred while getting future results or rendering image", e);
            throw new RuntimeException("Failed to generate report image", e);
        }
    }




    private <T> CompletableFuture<T> fetchData(String url, Class<T> clazz) {
        return CompletableFuture.supplyAsync(() -> {
            Request request = new Request.Builder().url(url)
                    .addHeader(HttpHeaders.USER_AGENT, "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome")
                    .build();
            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    throw new IOException("Unexpected code " + response);
                }
                if (response.body() == null) {
                    throw new IOException("Empty response body from " + url);
                }
                String json = response.body().string();
                return objectMapper.readValue(json, clazz);
            } catch (IOException e) {
                log.error("Failed to fetch data from {}", url, e);
                return null;
            }
        }, virtualThreadExecutor);
    }

    private CompletableFuture<List<String>> fetchItNewsData() {
        return CompletableFuture.supplyAsync(() -> {
            Request request = new Request.Builder().url(IT_RSS_URL)
                    .addHeader(HttpHeaders.USER_AGENT, "Mozilla/5.0")
                    .build();
            try (Response response = httpClient.newCall(request).execute()) {
                if (response.body() == null) {
                    return Collections.emptyList();
                }
                String xmlContent = response.body().string();

                // 简单的 XML 解析
                DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
                DocumentBuilder builder = factory.newDocumentBuilder();
                Document doc = builder.parse(new InputSource(new StringReader(xmlContent)));

                NodeList items = doc.getElementsByTagName("item");
                List<String> titles = new ArrayList<>();
                for (int i = 0; i < items.getLength() && titles.size() < 11; i++) {
                    Element item = (Element) items.item(i);
                    NodeList titleNode = item.getElementsByTagName("title");
                    if (titleNode.getLength() > 0) {
                        titles.add(titleNode.item(0).getTextContent());
                    }
                }
                return titles;
            } catch (Exception e) {
                log.error("Failed to fetch IT news", e);
                return Collections.emptyList();
            }
        }, virtualThreadExecutor);
    }

    /**
     * 获取 Bangumi 每日番剧
     */
    private CompletableFuture<List<AnimeItem>> fetchAnimeData() {
        return CompletableFuture.supplyAsync(() -> {
            Request request = new Request.Builder()
                    .addHeader(HttpHeaders.USER_AGENT, "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome")
                    .url(ANIME_API_URL).build();
            try (Response response = httpClient.newCall(request).execute()) {
                if (response.body() == null) return Collections.emptyList();

                JsonNode root = objectMapper.readTree(response.body().string());
                int dayOfWeek = LocalDate.now().getDayOfWeek().getValue(); // 1 (Mon) - 7 (Sun)

                // Bangumi API 返回的是一个数组，通常索引对应周几，但也需要检查 weekday.id
                // API 结构: [ {"weekday": {"en": "Mon", "id": 1}, "items": [...]}, ... ]

                JsonNode todayItems = null;
                for (JsonNode day : root) {
                    if (day.get("weekday").get("id").asInt() == dayOfWeek) {
                        todayItems = day.get("items");
                        break;
                    }
                }

                // 如果没找到今天的（比如API变动），取最后一个作为 fallback，模仿 Python 逻辑
                if (todayItems == null && root.size() > 0) {
                    todayItems = root.get(root.size() - 1).get("items");
                }

                List<AnimeItem> animeList = new ArrayList<>();
                if (todayItems != null) {
                    for (JsonNode item : todayItems) {
//                        if (animeList.size() >= 8) break;
                        String name = item.has("name_cn") && !item.get("name_cn").asText().isEmpty()
                                ? item.get("name_cn").asText()
                                : item.get("name").asText();
                        String image = "";
                        if (item.has("images") && item.get("images").has("large")) {
                            image = item.get("images").get("large").asText();
                        } else if (item.has("images") && item.get("images").has("common")) {
                            image = item.get("images").get("common").asText();
                        }

                        // 替换 http 为 https 防止图片加载失败
                        if (image.startsWith("http:")) {
                            image = image.replace("http:", "https:");
                        }

                        animeList.add(new AnimeItem(name, image));
                    }
                }
                return animeList;
            } catch (Exception e) {
                log.error("Failed to fetch Anime data", e);
                return Collections.emptyList();
            }
        }, virtualThreadExecutor);
    }

    private CompletableFuture<NewsDataDTO> fetchNewsData() {
        return fetchData(properties.getAlapiUrl(), NewsDataDTO.class);
    }

    private byte[] renderHtmlToImage(Map<String, Object> data) throws IOException {
        final Context context = new Context();
        context.setVariables(data);

        // 1. 读取并注入 CSS (保持不变)
        String cssContent = new String(DynamicResourceLoader.getInputStream("templates/winefox_daily_report/res/css/style.css").readAllBytes(), StandardCharsets.UTF_8);
        context.setVariable(CONTEXT_VARIABLE_CSS_STYLE, cssContent);

        // 2. 将图片资源转换为 Base64 并注入 Context
        context.setVariable("imgCharacter",  DynamicResourceLoader.getResourceAsBase64(RandomUtil.randomEle(CHARACTER_IMAGES)));
        context.setVariable("imgBottom",  DynamicResourceLoader.getResourceAsBase64("templates/winefox_daily_report/res/image/bottom.png"));
        context.setVariable("iconNews",  DynamicResourceLoader.getResourceAsBase64("templates/winefox_daily_report/res/icon/60.png"));
        context.setVariable("iconFish",  DynamicResourceLoader.getResourceAsBase64("templates/winefox_daily_report/res/icon/fish.png"));
        context.setVariable("iconBili",  DynamicResourceLoader.getResourceAsBase64("templates/winefox_daily_report/res/icon/bilibili.png"));
        context.setVariable("iconGame",  DynamicResourceLoader.getResourceAsBase64("templates/winefox_daily_report/res/icon/game.png"));
        context.setVariable("iconBgm",  DynamicResourceLoader.getResourceAsBase64("templates/winefox_daily_report/res/icon/bgm.png"));
        context.setVariable("iconIt",  DynamicResourceLoader.getResourceAsBase64("templates/winefox_daily_report/res/icon/it.png"));
        context.setVariable("iconHitokoto",  DynamicResourceLoader.getResourceAsBase64("templates/winefox_daily_report/res/icon/hitokoto.png"));
        final String htmlContent = templateEngine.process("winefox_daily_report/main", context);

        try (Page page = browser.newPage(new Browser.NewPageOptions().setDeviceScaleFactor(playwrightConfig.getDeviceScaleFactor()))) {
            page.setContent(htmlContent);
            Locator container = page.locator(PAGE_CONTAINER_SELECTOR);
            container.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.ATTACHED));
            return container.screenshot(new Locator.ScreenshotOptions()
                    .setType(ScreenshotType.PNG));
        }
    }

    private String getCachePathForToday() {
        return DAILY_REPORT_CACHE_DIR + "/" + LocalDate.now().format(DATE_FORMATTER) + ".png";
    }
}
