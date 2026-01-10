package com.github.winefoxbot.plugins.fortune.service.impl;

import com.github.winefoxbot.core.config.playwright.PlaywrightConfig;
import com.github.winefoxbot.core.utils.Base64Utils;
import com.github.winefoxbot.plugins.fortune.model.vo.FortuneRenderVO;
import com.github.winefoxbot.plugins.fortune.service.FortuneRenderService;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.ScreenshotType;
import com.microsoft.playwright.options.WaitForSelectorState;
import com.microsoft.playwright.options.WaitUntilState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;


@Service
@Slf4j
@RequiredArgsConstructor
public class FortuneRenderServiceImpl implements FortuneRenderService {

    private final TemplateEngine templateEngine;
    private final Browser browser;
    private final PlaywrightConfig playwrightConfig;

    private static final String HTML_TEMPLATE = "fortune/main";

    @Override
    public byte[] render(FortuneRenderVO data) {
        // 1. 准备 Thymeleaf 上下文
        Context context = new Context();
        context.setVariable("username", data.username());
        context.setVariable("dateStr", data.dateStr());
        context.setVariable("title", data.title());
        context.setVariable("description", data.description());
        context.setVariable("extraMessage", data.extraMessage());
        context.setVariable("starCount", data.starCount());

        // 如果有图片URL，尝试转换为Base64，避免Playwright加载超时
        if (data.imageUrl() != null && !data.imageUrl().isEmpty()) {
            try {
                // 下载图片并转为 Base64
                String base64Image = Base64Utils.toBase64String(data.imageUrl());
                context.setVariable("imageUrl", base64Image);
            } catch (Exception e) {
                log.error("下载运势图片失败: {}", data.imageUrl(), e);
                // 失败时回退到原始 URL，或者设为 null 显示默认图
                context.setVariable("imageUrl", data.imageUrl());
            }
        } else {
             context.setVariable("imageUrl", null);
        }

        context.setVariable("themeColor", data.themeColor());

        log.info("Fortune image processed for: {}", data.username());

        // 2. 渲染 HTML 字符串
        String htmlContent = templateEngine.process(HTML_TEMPLATE, context);

        // 3. Playwright 截图
        // 配置浏览器上下文：设置 User-Agent 伪装成普通浏览器，防止图片加载被拦截
        Browser.NewPageOptions pageOptions = new Browser.NewPageOptions()
                .setDeviceScaleFactor(playwrightConfig.getDeviceScaleFactor())
                .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");

        // 使用 JDK 7+ try-with-resources 自动关闭 Page
        try (Page page = browser.newPage(pageOptions)) {
            // 加载 HTML 内容
            // 如果是 Base64 图片，页面内容加载完图片就已经在了，DOMCONTENTLOADED 足够
            page.setContent(htmlContent, new Page.SetContentOptions().setWaitUntil(WaitUntilState.DOMCONTENTLOADED));

            // 只需要等待容器可见即可
            page.waitForSelector(".container", new Page.WaitForSelectorOptions().setState(WaitForSelectorState.VISIBLE));

            // 定位到 .container 元素进行截图 (配合 CSS 透明背景)
            return page.locator(".container").screenshot(
                    new Locator.ScreenshotOptions().setType(ScreenshotType.PNG)
            );
        }
    }
}
