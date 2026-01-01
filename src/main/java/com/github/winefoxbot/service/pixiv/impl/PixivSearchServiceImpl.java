package com.github.winefoxbot.service.pixiv.impl;

import cn.hutool.core.util.URLUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.winefoxbot.config.PixivConfig;
import com.github.winefoxbot.model.dto.pixiv.PixivApiResult;
import com.github.winefoxbot.model.dto.pixiv.PixivSearchParams;
import com.github.winefoxbot.model.dto.pixiv.PixivSearchResult;
import com.github.winefoxbot.service.pixiv.PixivSearchService;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.ScreenshotType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.font.FontRenderContext;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class PixivSearchServiceImpl implements PixivSearchService {

    private final PixivConfig pixivConfig;
    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final Browser browser;
    private final TemplateEngine templateEngine;
    private final ResourcePatternResolver resourceResolver;
    // --- 渲染常量 ---
    private static final int GRID_COLS = 6;
    private static final int GRID_ROWS = 10;
    private static final int CELL_WIDTH = 250;
    private static final int CELL_HEIGHT = 320;
    private static final int GAP = 12;
    private static final int IMG_HEIGHT = 250;

    private static final String API_URL = "https://www.pixiv.net/ajax/search/artworks/%s?word=%s&order=date_d&mode=%s&p=%d&s_mode=s_tag&type=all&lang=zh";
    private static final String HTML_TEMPLATE = "pixiv_search_result/main";
    private static final String RESOURCE_BASE_PATH = "templates/pixiv_search_result/res";
    // Fonts
    private static final Font TITLE_FONT = new Font("SansSerif", Font.BOLD, 14);
    private static final Font USER_FONT = new Font("SansSerif", Font.PLAIN, 12);
    private static final Font NUMBER_FONT = new Font("SansSerif", Font.BOLD, 28);

    @Override
    public PixivSearchResult search(PixivSearchParams params) {
        try {
            // 1. 获取 API 数据 (逻辑不变)
            PixivApiResult apiResult = fetchApiData(params);
            List<PixivApiResult.Artwork> artworks = apiResult.getBody().getIllustManga().getData();
            long totalArtworks = apiResult.getBody().getIllustManga().getTotal();
            int totalPages = totalArtworks > 0 ? (int) Math.ceil((double) totalArtworks / 60) : 0;

            if (artworks.isEmpty()) {
                // ... [处理无结果的情况，逻辑不变] ...
                return PixivSearchResult.builder()
                        .screenshot(createBlankImage("No artworks found for tags: " + String.join(" ", params.getTags())))
                        .artworks(List.of())
                        .currentPage(params.getPageNo())
                        .totalPages(0)
                        .totalArtworks(0)
                        .r18(params.isR18())
                        .build();
            }

            // 2. 使用 Java AWT 渲染核心的网格图
            byte[] gridImageBytes = renderGridImage(artworks);
            log.info("Successfully rendered artwork grid using Java AWT.");

            // 3. 准备模板变量
            Context context = new Context();
            context.setVariable("tags", String.join(" ", params.getTags()));
            context.setVariable("currentPage", params.getPageNo());
            context.setVariable("totalPages", totalPages);
            context.setVariable("totalArtworks", totalArtworks);
            // 将网格图转换为Base64 Data URI
            String gridImageBase64 = "data:image/png;base64," + Base64.getEncoder().encodeToString(gridImageBytes);
            context.setVariable("gridImage", gridImageBase64);
            // 加载所有本地资源 ----
            Map<String, String> res = loadResourcesAsDataUri(RESOURCE_BASE_PATH);
            context.setVariable("res", res);
            context.setVariable("hint_text","请不要在其它群聊分享或宣传此功能，本功能为酒狐独有");

            // 4. 使用 Thymeleaf 生成最终的 HTML
            // 注意模板路径，根据你的文件结构，可能是 "pixiv_search_result/main"
            String finalHtml = templateEngine.process(HTML_TEMPLATE, context);

            // 5. 使用 Playwright 将 HTML 渲染为最终图片
            byte[] finalScreenshot;
            try (BrowserContext browserContext = browser.newContext(
                    new Browser.NewContextOptions().setDeviceScaleFactor(2))) {
                Page page = browserContext.newPage();
                page.setContent(finalHtml);
                Locator container = page.locator(".container");
                finalScreenshot =  container.screenshot(new Locator.ScreenshotOptions().setType(ScreenshotType.PNG));
                log.info("Successfully captured final report screenshot using Playwright.");
            }

            // 6. 整理并返回结果
            List<PixivSearchResult.ArtworkData> artworkDataList = artworks.stream()
                    .map(a -> PixivSearchResult.ArtworkData.builder().pid(a.getId()).authorId(a.getUserId()).build())
                    .collect(Collectors.toList());

            return PixivSearchResult.builder()
                    .screenshot(finalScreenshot) // 返回最终的模板截图
                    .artworks(artworkDataList)
                    .currentPage(params.getPageNo())
                    .totalPages(totalPages)
                    .totalArtworks(totalArtworks)
                    .r18(params.isR18())
                    .build();

        } catch (Exception e) {
            log.error("An error occurred during hybrid Pixiv search and rendering", e);
            e.printStackTrace();
            throw new RuntimeException("Failed to perform hybrid Pixiv search", e);
        }
    }


    // 将API获取逻辑封装成一个私有方法
    private PixivApiResult fetchApiData(PixivSearchParams params) throws IOException {
        String tags = params.getTags().stream().map(URLUtil::encode).collect(Collectors.joining(" "));
        String mode = params.isR18() ? "r18" : "safe";
        String apiUrl = API_URL.formatted(tags, tags, mode, params.getPageNo());

        Request request = new Request.Builder()
                .url(apiUrl)
                .headers(pixivConfig.getHeaders())
                .build();
        log.info("Requesting Pixiv API: {}", apiUrl);
        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                log.error("API request failed with code {}: {}", response.code(), response.body() != null ? response.body().string() : "No body");
                throw new IOException("API request failed: " + response.code());
            }
            String jsonBody = response.body().string();
            PixivApiResult apiResult = objectMapper.readValue(jsonBody, PixivApiResult.class);
            if (apiResult.isError() || apiResult.getBody() == null || apiResult.getBody().getIllustManga() == null) {
                throw new IllegalStateException("Pixiv API returned an error or unexpected structure.");
            }
            return apiResult;
        }
    }

    private byte[] renderGridImage(List<PixivApiResult.Artwork> artworks) throws IOException {
        int canvasWidth = GRID_COLS * CELL_WIDTH + (GRID_COLS - 1) * GAP;
        int canvasHeight = GRID_ROWS * CELL_HEIGHT + (GRID_ROWS - 1) * GAP;

        BufferedImage canvas = new BufferedImage(canvasWidth, canvasHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = canvas.createGraphics();

        // --- 高质量渲染设置 ---
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);

        // Fill background
        g2d.setColor(Color.WHITE);
        g2d.fillRect(0, 0, canvasWidth, canvasHeight);

        // 并行下载图片
        List<CompletableFuture<BufferedImage>> imageFutures = artworks.stream()
                .map(artwork -> CompletableFuture.supplyAsync(() -> loadImage(artwork.getUrl())))
                .collect(Collectors.toList());

        for (int i = 0; i < artworks.size(); i++) {
            int row = i / GRID_COLS;
            int col = i % GRID_COLS;
            int x = col * (CELL_WIDTH + GAP);
            int y = row * (CELL_HEIGHT + GAP);

            PixivApiResult.Artwork artwork = artworks.get(i);

            // 获取下载好的图片
            BufferedImage image = imageFutures.get(i).join();

            // 绘制卡片背景
            g2d.setColor(Color.WHITE);
            g2d.fillRoundRect(x, y, CELL_WIDTH, CELL_HEIGHT, 16, 16);

            // 绘制图片
            if (image != null) {
                g2d.setClip(x, y, CELL_WIDTH, IMG_HEIGHT);
                g2d.drawImage(image, x, y, CELL_WIDTH, IMG_HEIGHT, null);
                g2d.setClip(null); // Clear clip
            }

            // 绘制数字角标
            drawNumberBadge(g2d, x, y, i + 1);

            // 绘制标题
            g2d.setColor(Color.BLACK);
            g2d.setFont(TITLE_FONT);
            drawTextWithEllipsis(g2d, artwork.getTitle(), x + 10, y + IMG_HEIGHT + 20, CELL_WIDTH - 20);

            // 绘制作者
            g2d.setColor(Color.GRAY);
            g2d.setFont(USER_FONT);
            drawTextWithEllipsis(g2d, artwork.getUserName(), x + 10, y + IMG_HEIGHT + 40, CELL_WIDTH - 20);
        }

        g2d.dispose();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(canvas, "png", baos);
        return baos.toByteArray();
    }

    private void drawNumberBadge(Graphics2D g2d, int cellX, int cellY, int number) {
        String numStr = String.valueOf(number);
        int badgeSize = 50;
        int badgeX = cellX + CELL_WIDTH / 2 - badgeSize / 2;
        int badgeY = cellY + IMG_HEIGHT / 2 - badgeSize / 2;

        // Shadow
        g2d.setColor(new Color(0, 0, 0, 100));
        g2d.fillOval(badgeX + 2, badgeY + 2, badgeSize, badgeSize);

        // Background
        g2d.setColor(new Color(40, 40, 40, 220));
        g2d.fillOval(badgeX, badgeY, badgeSize, badgeSize);

        // Text
        g2d.setColor(Color.WHITE);
        g2d.setFont(NUMBER_FONT);
        FontRenderContext frc = g2d.getFontRenderContext();
        Rectangle2D bounds = NUMBER_FONT.getStringBounds(numStr, frc);
        int textX = badgeX + (badgeSize - (int) bounds.getWidth()) / 2;
        int textY = badgeY + (badgeSize - (int) bounds.getHeight()) / 2 + (int) bounds.getHeight() - 4; // Adjust baseline
        g2d.drawString(numStr, textX, textY);
    }

    private void drawTextWithEllipsis(Graphics2D g2d, String text, int x, int y, int maxWidth) {
        FontMetrics fm = g2d.getFontMetrics();
        if (fm.stringWidth(text) <= maxWidth) {
            g2d.drawString(text, x, y);
        } else {
            String ellipsis = "...";
            int ellipsisWidth = fm.stringWidth(ellipsis);
            String truncatedText = text;
            while (fm.stringWidth(truncatedText) + ellipsisWidth > maxWidth && truncatedText.length() > 0) {
                truncatedText = truncatedText.substring(0, truncatedText.length() - 1);
            }
            g2d.drawString(truncatedText + ellipsis, x, y);
        }
    }

    private BufferedImage loadImage(String url) {
        try {
            // Pixiv's thumbnail proxy is slow, let's replace it with the faster i.pximg.net
            String imageUrl = url.replace("i.pximg.net/c/250x250_80_a2", "i.pximg.net");
            Request request = new Request.Builder()
                    .url(imageUrl)
                    .addHeader("Referer", "https://www.pixiv.net/")
                    .build();
            try (Response response = httpClient.newCall(request).execute(); InputStream in = response.body().byteStream()) {
                return ImageIO.read(in);
            }
        } catch (Exception e) {
            log.warn("Failed to load image from {}: {}", url, e.getMessage());
            // 返回一个占位图
            BufferedImage placeholder = new BufferedImage(CELL_WIDTH, IMG_HEIGHT, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = placeholder.createGraphics();
            g.setColor(Color.LIGHT_GRAY);
            g.fillRect(0, 0, CELL_WIDTH, IMG_HEIGHT);
            g.setColor(Color.DARK_GRAY);
            g.drawString("Load Failed", 20, 20);
            g.dispose();
            return placeholder;
        }
    }

    private byte[] createBlankImage(String message) throws IOException {
        BufferedImage image = new BufferedImage(500, 200, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, 500, 200);
        g.setColor(Color.BLACK);
        g.setFont(new Font("SansSerif", Font.BOLD, 20));
        g.drawString(message, 50, 100);
        g.dispose();
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(image, "png", baos);
        return baos.toByteArray();
    }

    /**
     * 扫描指定基础路径下的所有资源文件，并将它们转换为 Data URI 格式的 Map。
     *
     * @param basePath a classpath-relative path, e.g., "templates/pixiv_search_result"
     * @return A map where key is filename (e.g., "bot_icon.png") and value is the Data URI string.
     */
    private Map<String, String> loadResourcesAsDataUri(String basePath) {
        // 注入 ResourcePatternResolver 来实现这个功能
        // (需要在构造函数中添加 ResourcePatternResolver resourceResolver)
        Map<String, String> resourceMap = new HashMap<>();
        String locationPattern = ResourcePatternResolver.CLASSPATH_ALL_URL_PREFIX + basePath + "/**/*.*";

        log.info("Scanning for resources with pattern: {}", locationPattern);
        try {
            Resource[] resources = resourceResolver.getResources(locationPattern);
            for (Resource resource : resources) {
                if (resource.isReadable()) {
                    String filename = resource.getFilename();
                    if (filename != null) {
                        // 相对路径作为Key，例如 "image/bot_icon.png"
                        String relativePath = new URI(basePath).relativize(new URI(resource.getURI().toString().split(basePath)[1])).getPath();
                        resourceMap.put(relativePath.substring(1), loadResourceAsDataUri(resource)); // 去掉开头的'/'
                        log.debug("Loaded resource: {} as key: {}", resource.getDescription(), relativePath.substring(1));
                    }
                }
            }
        } catch (Exception e) {
            log.error("Could not find or access resources for pattern: {}.", locationPattern, e);
        }
        return resourceMap;
    }

    private String loadResourceAsDataUri(Resource resource) throws IOException {
        byte[] fileBytes = resource.getInputStream().readAllBytes();
        String mimeType = "application/octet-stream";
        String filename = resource.getFilename();
        if (filename != null) {
            if (filename.endsWith(".png")) mimeType = "image/png";
            else if (filename.endsWith(".jpg") || filename.endsWith(".jpeg")) mimeType = "image/jpeg";
            else if (filename.endsWith(".svg")) mimeType = "image/svg+xml"; // 支持SVG
            else if (filename.endsWith(".gif")) mimeType = "image/gif";
        }
        return "data:" + mimeType + ";base64," + Base64.getEncoder().encodeToString(fileBytes);
    }
}
