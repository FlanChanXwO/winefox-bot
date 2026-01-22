package com.github.winefoxbot.plugins.imgexploration.service;

import com.github.winefoxbot.core.config.playwright.PlaywrightConfig;
import com.github.winefoxbot.core.utils.DynamicResourceLoader; // 引入新工具类
import com.github.winefoxbot.plugins.imgexploration.model.dto.SearchResultItemDTO;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.ScreenshotType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j; // 加上日志
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.util.Base64;
import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class ImageExplorationRenderer {

    private final PlaywrightConfig playwrightConfig;
    private final Browser browser;
    private final TemplateEngine templateEngine;
    private final static String HTML_TEMPLATE_PATH = "img_exploration/main";

    private final static String CSS_PATH = "templates/img_exploration/res/css/style.css";
    // 缓存 CSS 内容
    private String cachedCss;

    /**
     * 渲染入口
     */
    public byte[] renderExplorationResult(List<SearchResultItemDTO> rawItems) {
        // 1. 准备 CSS
        String cssContent = getCssContent();

        // 2. 将 DTO 转换为适合模板渲染的 View Object
        List<RenderItem> renderItems = rawItems.stream()
                .map(this::convertToRenderItem)
                .toList();

        // 3. 创建 Thymeleaf 上下文
        Context context = new Context();
        context.setVariable("cssContent", cssContent); // 注入 CSS
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


    private String getCssContent() {
        // 如果已缓存且非空，直接返回 (注意：如果你希望生产环境热更新CSS，可以注释掉这行缓存判断)
        if (this.cachedCss != null) {
            return this.cachedCss;
        }

        try {
            // 使用 DynamicResourceLoader 智能加载 (支持 classpath 和 file system)
            String content = DynamicResourceLoader.getResourceAsString(CSS_PATH);
            this.cachedCss = content;
            return content;
        } catch (Exception e) {
            log.error("Failed to load CSS from path: {}", CSS_PATH, e);
            // 降级策略：返回空字符串，防止整个功能崩溃，顶多样式丑一点
            return "/* CSS Load Failed */";
        }
    }


    private RenderItem convertToRenderItem(SearchResultItemDTO dto) {
        String base64;
        if (dto.thumbnailBytes() != null && dto.thumbnailBytes().length > 0) {
            base64 = "data:image/jpeg;base64," + Base64.getEncoder().encodeToString(dto.thumbnailBytes());
        } else {
            base64 = "data:image/svg+xml;base64,PHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciIHdpZHRoPSIxMDAiIGhlaWdodD0iMTAwIj48cmVjdCB3aWR0aD0iMTAwJSIgaGVpZ2h0PSIxMDAlIiBmaWxsPSIjMzMzIi8+PHRleHQgeD0iNTAlIiB5PSI1MCUiIGRvbWluYW50LWJhc2VsaW5lPSJtaWRkbGUiIHRleHQtYW5jaG9yPSJtaWRkbGUiIGZpbGw9IiM2NjYiPk5vIEltYWdlPC90ZXh0Pjwvc3ZnPg==";
        }

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
