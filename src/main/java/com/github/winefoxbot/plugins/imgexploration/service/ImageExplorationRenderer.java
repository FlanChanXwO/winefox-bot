package com.github.winefoxbot.plugins.imgexploration.service;

import com.github.winefoxbot.core.config.playwright.PlaywrightConfig;
import com.github.winefoxbot.plugins.imgexploration.model.dto.SearchResultItemDTO;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.ScreenshotType;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ImageExplorationRenderer {

    private final PlaywrightConfig playwrightConfig;
    private final Browser browser;
    private final TemplateEngine templateEngine;
    private final static String HTML_TEMPLATE_PATH = "img_exploration/main";
    private final static String CSS_PATH = "templates/img_exploration/res/css/style.css";

    // 缓存 CSS 内容，避免重复 IO
    private String cachedCss;

    /**
     * 渲染入口
     */
    public byte[] renderExplorationResult(List<SearchResultItemDTO> rawItems) {
        // 1. 准备 CSS (如果还没加载)
        ensureCssLoaded();

        // 2. 将 DTO 转换为适合模板渲染的 View Object
        // 这一步是为了处理 Base64 转换和相似度数值解析，保持模板简洁
        List<RenderItem> renderItems = rawItems.stream()
                .map(this::convertToRenderItem)
                .toList();

        // 3. 创建 Thymeleaf 上下文
        Context context = new Context();
        context.setVariable("cssContent", cachedCss); // 注入 CSS
        context.setVariable("items", renderItems);    // 注入处理后的数据

        // 4. 渲染 HTML 字符串
        String htmlContent = templateEngine.process(HTML_TEMPLATE_PATH, context);

        // 5. Playwright 截图
        try (BrowserContext browserContext = browser.newContext(new Browser.NewContextOptions().setDeviceScaleFactor(playwrightConfig.getDeviceScaleFactor()));
             Page page = browserContext.newPage()) {
            page.setViewportSize(850, 1500);
            page.setContent(htmlContent);
            return page.locator(".container").screenshot(new Locator.ScreenshotOptions().setType(ScreenshotType.PNG));
        }
    }

    private void ensureCssLoaded() {
        if (cachedCss == null) {
            try {
                var resource = new ClassPathResource(CSS_PATH);
                cachedCss = resource.getContentAsString(StandardCharsets.UTF_8);
            } catch (IOException e) {
                // 生产环境可以降级为空字符串或打 Log
                cachedCss = "/* CSS Load Failed */";
                throw new RuntimeException("Failed to load dark theme css", e);
            }
        }
    }

    /**
     * 内部使用的 View Object，专门用于 Thymeleaf 展示
     */
    private RenderItem convertToRenderItem(SearchResultItemDTO dto) {
        // 图片转 Base64
        String base64;
        if (dto.thumbnailBytes() != null && dto.thumbnailBytes().length > 0) {
            base64 = "data:image/jpeg;base64," + Base64.getEncoder().encodeToString(dto.thumbnailBytes());
        } else {
            // 默认深色占位图 SVG
            base64 = "data:image/svg+xml;base64,PHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciIHdpZHRoPSIxMDAiIGhlaWdodD0iMTAwIj48cmVjdCB3aWR0aD0iMTAwJSIgaGVpZ2h0PSIxMDAlIiBmaWxsPSIjMzMzIi8+PHRleHQgeD0iNTAlIiB5PSI1MCUiIGRvbWluYW50LWJhc2VsaW5lPSJtaWRkbGUiIHRleHQtYW5jaG9yPSJtaWRkbGUiIGZpbGw9IiM2NjYiPk5vIEltYWdlPC90ZXh0Pjwvc3ZnPg==";
        }

        // 解析相似度数值 (用于 CSS 类判断)
        double score = 0.0;
        String display = dto.similarity() != null ? dto.similarity() : "N/A";
        try {
            if (dto.similarity() != null) {
                String clean = dto.similarity().replace("%", "").trim();
                score = Double.parseDouble(clean);
            }
        } catch (Exception ignored) {
        }

        return new RenderItem(
                dto.title(),
                dto.url(),
                base64,
                dto.source(),
                display,
                score,
                dto.description()
        );
    }

    private record RenderItem(
            String title,
            String url,
            String imageBase64,
            String source,
            String similarityDisplay,
            double similarityScore,
            String description
    ) {
    }
}
