package com.github.winefoxbot.plugins.dailyreport.service;


import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.winefoxbot.core.config.playwright.PlaywrightConfig;
import com.github.winefoxbot.core.service.file.FileStorageService;
import com.github.winefoxbot.core.utils.Base64Utils;
import com.github.winefoxbot.core.utils.ResourceLoader;
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
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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

    public static final String DATE_FORMAT_PATTERN = "yyyy-MM-dd";
    public static final String DAILY_REPORT_CACHE_DIR = "daily-report";
    public static final String HITOKOTO_API_URL = "https://v1.hitokoto.cn/?c=a&c=b&c=c&encode=json";
    public static final String BILI_HOTWORD_API_URL = "https://s.search.bilibili.com/main/hotword";

    public static final String CONTEXT_VARIABLE_HITOKOTO = "hitokoto";
    public static final String CONTEXT_VARIABLE_NEWS_DATA = "newsData";
    public static final String CONTEXT_VARIABLE_BILI_HOTWORDS = "biliHotwords";
    public static final String CONTEXT_VARIABLE_CSS_STYLE = "cssStyle";

    public static final String PAGE_CONTAINER_SELECTOR = ".wrapper";
    public static final int BILI_HOTWORDS_LIMIT = 10;



    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern(DATE_FORMAT_PATTERN);

    private final OkHttpClient httpClient;
    private final Browser browser;
    private final PlaywrightConfig playwrightConfig;
    private final ObjectMapper objectMapper;
    private final TemplateEngine templateEngine;
    private final DailyReportProperties properties;
    private final FileStorageService fileStorageService;
    private final ExecutorService virtualThreadExecutor = Executors.newVirtualThreadPerTaskExecutor();
    private final Lock lock = new ReentrantLock();

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
     * 强制重新生成当天的日报图片。
     *
     * @return 新生成的日报图片的字节数组
     * @throws IOException 如果文件读写或网络请求失败
     */
    public byte[] regenerateDailyReportImage() throws IOException {
        String cacheKey = getCachePathForToday();
        fileStorageService.deleteFile(cacheKey, null);
        return generateAndCacheReport(cacheKey);
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
        CompletableFuture<HitokotoDTO> hitokotoFuture = fetchData(HITOKOTO_API_URL, HitokotoDTO.class);
        CompletableFuture<NewsDataDTO> newsFuture = fetchNewsData();
        CompletableFuture<BiliHotwordDTO> biliHotwordFuture = fetchData(BILI_HOTWORD_API_URL, BiliHotwordDTO.class);

        CompletableFuture.allOf(hitokotoFuture, newsFuture, biliHotwordFuture).join();

        try {
            Map<String, Object> data = new HashMap<>();
            data.put(CONTEXT_VARIABLE_HITOKOTO, hitokotoFuture.get());
            data.put(CONTEXT_VARIABLE_NEWS_DATA, newsFuture.get().data());
            List<BiliHotwordDTO.HotwordItem> hotwords = biliHotwordFuture.get().list().stream()
                    .limit(BILI_HOTWORDS_LIMIT)
                    .collect(Collectors.toList());
            data.put(CONTEXT_VARIABLE_BILI_HOTWORDS, hotwords);

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

    private CompletableFuture<NewsDataDTO> fetchNewsData() {
        return fetchData(properties.getAlapiUrl(), NewsDataDTO.class);
    }

    private byte[] renderHtmlToImage(Map<String, Object> data) throws IOException {
        final Context context = new Context();
        context.setVariables(data);

        // 1. 读取并注入 CSS (保持不变)
        String cssContent = new String(ResourceLoader.getInputStream("classpath:templates/daily_report/res/css/style.css").readAllBytes(), StandardCharsets.UTF_8);
        context.setVariable(CONTEXT_VARIABLE_CSS_STYLE, cssContent);

        // 2. 将图片资源转换为 Base64 并注入 Context
        context.setVariable("imgCharacter", getResourceAsBase64("classpath:templates/daily_report/res/image/2.no-bg.png"));
        context.setVariable("imgBottom", getResourceAsBase64("classpath:templates/daily_report/res/image/bottom.png"));
        context.setVariable("iconNews", getResourceAsBase64("classpath:templates/daily_report/res/icon/60.png"));
        context.setVariable("iconFish", getResourceAsBase64("classpath:templates/daily_report/res/icon/fish.png"));
        context.setVariable("iconBili", getResourceAsBase64("classpath:templates/daily_report/res/icon/bilibili.png"));
        context.setVariable("iconGame", getResourceAsBase64("classpath:templates/daily_report/res/icon/game.png")); // 可选

        final String htmlContent = templateEngine.process(properties.getTemplatePath(), context);

        try (Page page = browser.newPage(new Browser.NewPageOptions().setDeviceScaleFactor(playwrightConfig.getDeviceScaleFactor()))) {
            page.setContent(htmlContent);
            Locator container = page.locator(PAGE_CONTAINER_SELECTOR);
            container.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.ATTACHED));
            return container.screenshot(new Locator.ScreenshotOptions()
                    .setType(ScreenshotType.PNG));
        }
    }

    /**
     * 读取资源文件并转换为 Base64 Data URI
     */
    private String getResourceAsBase64(String classpath) {
        try {
            byte[] bytes = ResourceLoader.getInputStream(classpath).readAllBytes();
            return Base64Utils.toBase64String(bytes);
        } catch (Exception e) {
            log.error("Failed to load resource: {}", classpath, e);
            return ""; // 返回空或者默认占位图
        }
    }

    private String getCachePathForToday() {
        return DAILY_REPORT_CACHE_DIR + "/" + LocalDate.now().format(DATE_FORMATTER) + ".png";
    }
}
