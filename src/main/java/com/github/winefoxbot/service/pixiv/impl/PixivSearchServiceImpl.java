package com.github.winefoxbot.service.pixiv.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.core.util.URLUtil;
import com.github.winefoxbot.config.PixivConfig;
import com.github.winefoxbot.model.dto.pixiv.PixivSearchParams;
import com.github.winefoxbot.model.dto.pixiv.PixivSearchResult;
import com.github.winefoxbot.service.pixiv.PixivSearchService;
import com.microsoft.playwright.*;
import com.microsoft.playwright.options.Cookie;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.ScreenshotType;
import com.microsoft.playwright.options.WaitForSelectorState;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.io.IOException;
import java.net.URI;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class PixivSearchServiceImpl implements PixivSearchService {

    private final PixivConfig pixivConfig;
    private final Browser browser;
    private final TemplateEngine templateEngine;
    private final ResourcePatternResolver resourceResolver;

    // 【新增】用于并行下载图片的线程池
    private ExecutorService imageDownloadExecutor;

    // --- CSS 选择器常量 (保持不变) ---
    private static final String ARTWORK_CONTAINER_SELECTOR = "section:last-of-type:has(div > div > div > div)";
    private static final String ARTWORK_LIST_CSS_SELECTOR = ARTWORK_CONTAINER_SELECTOR + " ul";
    private static final String ARTWORK_LIST_ITEM_CSS_SELECTOR = ARTWORK_LIST_CSS_SELECTOR + " li";
    private static final String STAR_ICON_CSS_SELECTOR = ARTWORK_LIST_ITEM_CSS_SELECTOR + " button[type='button']";
    private static final String TOTAL_SPAN_CSS_SELECTOR = ARTWORK_CONTAINER_SELECTOR + " h3 + div span";
    private static final String ARTWORK_IMAGE_CSS_SELECTOR = ARTWORK_LIST_ITEM_CSS_SELECTOR + " a > div > div > img";

    // --- 正则表达式 (保持不变) ---
    private static final Pattern PID_PATTERN = Pattern.compile("/artworks/(\\d+)");
    private static final Pattern UID_PATTERN = Pattern.compile("/users/(\\d+)");


    private static final String BLUR_STRENTH = "5px";


    private static final String HTML_TEMPLATE = "pixiv_search_result/main";
    private static final String RESOURCE_BASE_PATH = "templates/pixiv_search_result/res";


    @Data
    @Builder
    public static class ArtworkViewData {
        private String pid;
        private String uid;
        private String title;
        private String userName;
        private String imageBase64; // 图片的 Base64 Data URI
        private int itemIndex;     // 列表中的索引，用于显示序号 (从 0 开始)
    }

    @PostConstruct
    public void init() {
        int concurrencyLevel = 16; // 浏览器环境 IO 密集，可以适当增加并发数
        imageDownloadExecutor = Executors.newFixedThreadPool(concurrencyLevel);
        log.info("PixivSearchService image download executor initialized with {} threads.", concurrencyLevel);
    }

    @PreDestroy
    public void destroy() {
        if (imageDownloadExecutor != null && !imageDownloadExecutor.isShutdown()) {
            log.info("Shutting down PixivSearchService image download executor...");
            imageDownloadExecutor.shutdown();
        }
        // browser 的关闭由 Spring Boot 的 @PreDestroy hook 自动处理，无需在此处手动关闭
    }


    @Override
    public PixivSearchResult search(PixivSearchParams params) {
        try (BrowserContext context = createBrowserContext()) {
            Page page = context.newPage();

            // 步骤 1: 导航并加载原始页面
            String searchUrl = buildSearchUrl(params);
            log.info("Navigating to Pixiv search URL: {}", searchUrl);
            page.navigate(searchUrl, new Page.NavigateOptions().setTimeout(60000));
            page.waitForLoadState(LoadState.NETWORKIDLE, new Page.WaitForLoadStateOptions().setTimeout(30000));
            log.info("Page loaded. URL: {}", page.url());

            smoothAutoScroll(page);
            log.info("Page scrolling complete.");

            // 步骤 2: 提取元数据
            long totalArtworks = extractTotalArtworks(page);
            int totalPages = totalArtworks > 0 ? (int) Math.ceil((double) totalArtworks / 60) : 0;
            List<PixivSearchResult.ArtworkData> artworksData = extractArtworksPidsAndUids(page);

            if (artworksData.isEmpty()) {
                log.warn("No artworks found on the page for tags: {}", params.getTags());
                return PixivSearchResult.builder().artworks(List.of()).totalPages(0).totalArtworks(0).screenshot(new byte[0]).build();
            }

            // 步骤 3: 在浏览器中直接操作DOM，准备截图区域
            // 移除收藏按钮
            removeStarIcons(page);
            // 如果是R18，进行模糊处理
            if (params.isR18()) {
                blurArtworks(page);
            }
            // 给每个作品添加序号
            addNumberingToArtworks(page);

            // 步骤 4: 截取核心作品列表区域
            Locator artworkContainer = page.locator(ARTWORK_LIST_CSS_SELECTOR);
            byte[] gridImageBytes = artworkContainer.screenshot(new Locator.ScreenshotOptions().setType(ScreenshotType.PNG));
            String gridImageBase64 = "data:image/png;base64," + Base64.getEncoder().encodeToString(gridImageBytes);
            log.info("Artwork grid screenshot captured successfully, size: {} bytes.", gridImageBytes.length);

            // 步骤 5: 准备最终模板所需的所有数据
            Map<String, String> res = loadResourcesAsDataUri(RESOURCE_BASE_PATH);
            Context thymeleafContext = new Context();
            thymeleafContext.setVariable("tags", String.join(" ", params.getTags()));
            thymeleafContext.setVariable("currentPage", params.getPageNo());
            thymeleafContext.setVariable("totalPages", totalPages);
            thymeleafContext.setVariable("totalArtworks", totalArtworks);
            thymeleafContext.setVariable("res", res);
            thymeleafContext.setVariable("hint_text", "请不要在其它群聊分享或宣传此功能，本功能为酒狐独有");
            // 【关键】将截取到的作品列表图片传递给模板
            thymeleafContext.setVariable("gridImage", gridImageBase64);

            String finalHtml = templateEngine.process(HTML_TEMPLATE, thymeleafContext);

            byte[] finalScreenshot;
            try (BrowserContext screenshotContext = browser.newContext(new Browser.NewContextOptions().setDeviceScaleFactor(2))) {
                Page screenshotPage = screenshotContext.newPage();
                // 视口宽度最好与模板中的 .container 宽度匹配或更大
                screenshotPage.setContent(finalHtml);
                // 【关键修复】 定位到那张Base64图片，并等待它加载完成。
                // 这会确保在截图时，图片已经解码并渲染在页面上。
                // 我们选择等待的图片是 <div class="pixiv-card"> 里的那张。
                Locator gridImageLocator = screenshotPage.locator(".pixiv-card img");
                gridImageLocator.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE).setTimeout(10000)); // 等待图片可见，设置10秒超时

                log.info("Embedded grid image is now visible in the final template.");

                // 现在截图，目标容器是.container，而不是.pixiv-card，以便截取完整的卡片
                Locator container = screenshotPage.locator(".container");
                finalScreenshot = container.screenshot(new Locator.ScreenshotOptions().setType(ScreenshotType.PNG));
                log.info("Final composite image captured successfully.");
            }

            return PixivSearchResult.builder()
                    .screenshot(finalScreenshot)
                    .artworks(artworksData)
                    .currentPage(params.getPageNo())
                    .totalPages(totalPages)
                    .totalArtworks(totalArtworks)
                    .r18(params.isR18())
                    .build();

        } catch (Exception e) {
            log.error("An error occurred during Pixiv search with composite image generation", e);
            throw new RuntimeException("Failed to perform Pixiv search", e);
        }
    }


    // 模糊处理
    private void blurArtworks(Page page) {
        log.info("Applying blur filter for R18 content...");
        page.evaluate("selector => document.querySelectorAll(selector).forEach(img => img.style.filter = 'blur(%s)')".formatted(BLUR_STRENTH), ARTWORK_IMAGE_CSS_SELECTOR);
    }

    // 移除收藏按钮
    private void removeStarIcons(Page page) {
        log.info("Removing star icons...");
        page.evaluate("selector => document.querySelectorAll(selector).forEach(btn => btn.remove())", STAR_ICON_CSS_SELECTOR);
    }

    // 提取PID和UID，用于返回给调用者
    private List<PixivSearchResult.ArtworkData> extractArtworksPidsAndUids(Page page) {
        String jsScript = """
            selector => {
                const artworks = document.querySelectorAll(selector);
                const data = [];
                artworks.forEach(item => {
                    const artworkLink = item.querySelector('a[href*="/artworks/"]');
                    const authorLink = item.querySelector('a[href*="/users/"]');
                    if (artworkLink && authorLink) {
                        data.push({
                            artworkUrl: artworkLink.href,
                            authorUrl: authorLink.href
                        });
                    }
                });
                return data;
            }
        """;
        @SuppressWarnings("unchecked")
        List<Map<String, String>> rawData = (List<Map<String, String>>) page.evaluate(jsScript, ARTWORK_LIST_ITEM_CSS_SELECTOR);

        return rawData.stream().map(raw -> {
            Matcher pidMatcher = PID_PATTERN.matcher(raw.getOrDefault("artworkUrl", ""));
            Matcher uidMatcher = UID_PATTERN.matcher(raw.getOrDefault("authorUrl", ""));
            String pid = pidMatcher.find() ? pidMatcher.group(1) : null;
            String uid = uidMatcher.find() ? uidMatcher.group(1) : null;
            return PixivSearchResult.ArtworkData.builder().pid(pid).authorId(uid).build();
        }).collect(Collectors.toList());
    }

    private BrowserContext createBrowserContext() {
        BrowserContext context = browser.newContext(new Browser.NewContextOptions().setLocale("zh-CN"));
        List<Cookie> cookies = List.of(
                new Cookie("p_ab_id", pixivConfig.getPAbId()).setDomain(".pixiv.net").setPath("/"),
                new Cookie("PHPSESSID", pixivConfig.getPhpSessId()).setDomain(".pixiv.net").setPath("/")
        );
        context.addCookies(cookies);
        return context;
    }

    private String buildSearchUrl(PixivSearchParams params) {
        String tags = params.getTags().stream().map(URLUtil::encode).collect(Collectors.joining(" "));
        String mode = params.isR18() ? "r18" : "safe";
        return "https://www.pixiv.net/tags/%s/artworks?s_mode=s_tag&mode=%s&p=%d".formatted(tags, mode, params.getPageNo());
    }

    private long extractTotalArtworks(Page page) {
        try {
            ElementHandle totalSpan = page.querySelector(TOTAL_SPAN_CSS_SELECTOR);
            if (totalSpan != null) {
                String textContent = totalSpan.textContent();
                if (StrUtil.isNotBlank(textContent)) {
                    // 移除所有非数字字符，例如逗号
                    return Long.parseLong(textContent.replaceAll("[^0-9]", ""));
                }
            }
            log.warn("Could not find total artworks span with selector: {}", TOTAL_SPAN_CSS_SELECTOR);
        } catch (Exception e) {
            log.warn("Could not extract total artworks count, defaulting to 0.", e);
        }
        return 0;
    }

    private void smoothAutoScroll(Page page) {
        page.evaluate("() => { window.scrollTo(0, 0); }");
        for (int i = 0; i < 15; i++) { // 增加滚动次数以确保加载完全
            page.evaluate("window.scrollBy(0, window.innerHeight)");
            try {
                page.waitForLoadState(LoadState.NETWORKIDLE, new Page.WaitForLoadStateOptions().setTimeout(1000));
            } catch (TimeoutError e) {
                log.trace("Network idle timeout during scroll, which is expected. Continuing...");
            }
        }
        log.info("Initial scrolling finished. Waiting for images to load...");
        try {
            // 等待图片加载，这是一个很好的实践
            page.waitForFunction(
                    "selector => Array.from(document.querySelectorAll(selector)).every(img => img.complete && img.naturalWidth > 0)",
                    ARTWORK_IMAGE_CSS_SELECTOR,
                    new Page.WaitForFunctionOptions().setTimeout(20000)
            );
            log.info("All images are confirmed to be loaded on the source page.");
        } catch (TimeoutError e) {
            log.warn("Timeout waiting for all images to load on source page. Some data might be incomplete.");
        }
    }

    private void addNumberingToArtworks(Page page) {
        log.info("Adding numbering to artworks...");
        String jsScript = """
            (selector) => {
                const artworks = document.querySelectorAll(selector);
                artworks.forEach((artwork, index) => {
                    if (artwork.querySelector('.number-label')) return; // 防止重复添加
                    artwork.style.position = 'relative';
                    const numberLabel = document.createElement('div');
                    numberLabel.className = 'number-label'; // 添加一个class便于识别
                    numberLabel.innerText = index + 1;
                    Object.assign(numberLabel.style, {
                        position: 'absolute', top: '50%', left: '50%',
                        transform: 'translate(-50%, -50%)', zIndex: '100',
                        backgroundColor: 'rgba(40, 40, 40, 0.8)', color: 'white',
                        borderRadius: '50%', width: '60px', height: '60px',
                        display: 'flex', alignItems: 'center', justifyContent: 'center',
                        fontSize: '28px', fontWeight: 'bold',
                        boxShadow: '0 0 5px rgba(0,0,0,0.7)'
                    });
                    artwork.appendChild(numberLabel);
                });
            }
        """;
        page.evaluate(jsScript, ARTWORK_LIST_ITEM_CSS_SELECTOR);
    }

    private Map<String, String> loadResourcesAsDataUri(String basePath) {
        Map<String, String> resourceMap = new HashMap<>();
        String locationPattern = ResourcePatternResolver.CLASSPATH_ALL_URL_PREFIX + basePath + "/**/*.*";
        try {
            Resource[] resources = resourceResolver.getResources(locationPattern);
            for (Resource resource : resources) {
                if (resource.isReadable()) {
                    String filename = resource.getFilename();
                    if (filename != null) {
                        String relativePath = new URI(basePath).relativize(new URI(resource.getURI().toString().split(basePath)[1])).getPath();
                        resourceMap.put(relativePath.substring(1), loadResourceAsDataUri(resource));
                    }
                }
            }
        } catch (Exception e) {
            log.error("Could not find or access resources for pattern: {}.", locationPattern, e);
        }
        return resourceMap;
    }

    // 【新增】将单个资源文件转换为 Data URI
    private String loadResourceAsDataUri(Resource resource) throws IOException {
        byte[] fileBytes = resource.getInputStream().readAllBytes();
        String mimeType = "application/octet-stream";
        String filename = resource.getFilename();
        if (filename != null) {
            if (filename.endsWith(".png")) mimeType = "image/png";
            else if (filename.endsWith(".jpg") || filename.endsWith(".jpeg")) mimeType = "image/jpeg";
            else if (filename.endsWith(".svg")) mimeType = "image/svg+xml";
            else if (filename.endsWith(".gif")) mimeType = "image/gif";
            else if (filename.endsWith(".css")) mimeType = "text/css";
        }
        return "data:" + mimeType + ";base64," + Base64.getEncoder().encodeToString(fileBytes);
    }
}
