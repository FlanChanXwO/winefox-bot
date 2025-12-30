package com.github.winefoxbot.service.core.impl;

import com.github.winefoxbot.init.HelpDocLoader;
import com.github.winefoxbot.model.dto.helpdoc.HelpData;
import com.github.winefoxbot.model.dto.helpdoc.HelpGroup;
import com.github.winefoxbot.service.core.HelpImageService;
import com.microsoft.playwright.*;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.stream.Stream;

/**
 * 帮助图片生成服务的实现类。
 * 使用 Playwright 渲染 Thymeleaf 模板来生成图片。
 * 使用 "真寻日报" 风格模板。
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class HelpImageServiceImpl implements HelpImageService {

    private final HelpDocLoader helpDocLoader;
    private final TemplateEngine templateEngine;
    private final ResourcePatternResolver resourceResolver;
    private Playwright playwright;
    private Browser browser;


    @Override
    public byte[] generateAllHelpImage(){
        // 1. 获取所有分组的数据
        HelpData allHelpData = helpDocLoader.getSortedHelpData();
        // 2. 渲染图片
        return renderHelpImage(allHelpData); // 传入标题
    }

    @Override
    public byte[] generateHelpImageByGroup(String groupName){
        // 1. 获取所有数据
        HelpData allHelpData = helpDocLoader.getSortedHelpData();

        // 2. 查找指定的分组
        Optional<HelpGroup> targetGroupOpt = allHelpData.getGroups().stream()
                .filter(g -> g.getName().equalsIgnoreCase(groupName.trim())) // 忽略大小写和前后空格
                .findFirst();

        if (targetGroupOpt.isEmpty()) {
            log.warn("Help group '{}' not found.", groupName);
            return null;
        }

        // 3. 创建一个新的HelpData对象，只包含这一个分组
        HelpData singleGroupData = new HelpData();
        singleGroupData.setDefaultIcon(allHelpData.getDefaultIcon());
        singleGroupData.setGroups(List.of(targetGroupOpt.get()));

        // 4. 渲染图片，标题就是分组名
        return renderHelpImage(singleGroupData);
    }


    @PostConstruct
    public void init() {
        try {
            log.info("Initializing Playwright and launching browser...");
            playwright = Playwright.create();
            BrowserType browserType = playwright.chromium();
            browser = browserType.launch(new BrowserType.LaunchOptions().setHeadless(true));
            log.info("Playwright initialized and browser launched successfully.");
        } catch (Exception e) {
            log.error("Failed to initialize Playwright. Help image generation will be disabled.", e);
            if (playwright != null) {
                playwright.close();
            }
            throw new RuntimeException("Could not initialize Playwright.", e);
        }
    }

    @PreDestroy
    public void destroy() {
        log.info("Closing Playwright and browser...");
        if (browser != null && browser.isConnected()) {
            browser.close();
        }
        if (playwright != null) {
            playwright.close();
        }
        log.info("Playwright closed.");
    }


    private byte[] renderHelpImage(HelpData helpData) {
        Context context = new Context();
        context.setVariable("help_data", helpData);
        context.setVariable("hint_text", "具体命令参数请查看详细说明或咨询管理员。");

        Map<String, String> imageResourcesAsDataUri = loadResourcesAsDataUri("templates/help_report/res");
        context.setVariable("res", imageResourcesAsDataUri);
        // [核心修改] 一次性完成所有渲染，不再需要手动拼接CSS
        String htmlContent = templateEngine.process("help_report/main", context);
        try (BrowserContext browserContext = browser.newContext(/*...*/)) {
            Page page = browserContext.newPage();
            try {
                // 直接使用完全渲染好的HTML
                page.setContent(htmlContent);
                Locator container = page.locator(".container");
                return container.screenshot(/*...*/);
            } finally {
                page.close();
            }
        }
    }

    private Map<String, String> loadResourcesAsDataUri(String basePath) {
        Map<String, String> resourceMap = new HashMap<>();

        // [关键修正] 在路径模式末尾添加 "/**"，使其可以扫描所有子目录
        String locationPattern = ResourcePatternResolver.CLASSPATH_ALL_URL_PREFIX + basePath + "/**/*.*";

        log.info("Scanning for resources with pattern (including subdirectories): {}", locationPattern);

        try {
            Resource[] resources = resourceResolver.getResources(locationPattern);

            if (resources.length == 0) {
                log.warn("CRITICAL: No resources found for pattern '{}'. Please check the path and build configuration.", locationPattern);
                return resourceMap;
            }

            log.info("Found {} resources for pattern '{}'.", resources.length, locationPattern);

            for (Resource resource : resources) {
                if (resource.isReadable()) {
                    try {
                        String filename = resource.getFilename();
                        if (filename != null) {
                            String dataUri = loadResourceAsDataUri(resource);
                            // [关键修改] 直接使用完整的文件名作为 key
                            String key = filename;
                            resourceMap.put(key, dataUri);
                            log.debug("Loaded resource: {} as key: {}", resource.getURI(), key);
                        }
                    } catch (IOException e) {
                        log.error("Failed to load resource as data URI: {}", resource.getDescription(), e);
                    }
                }
            }
        } catch (IOException e) {
            log.error("Could not find or access resources for pattern: {}. Resources will not be loaded.", locationPattern, e);
        }

        if (resourceMap.isEmpty()) {
            log.error("Critical: Resource map is empty after scanning. Images and styles will be missing.");
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
        }
        return "data:" + mimeType + ";base64," + Base64.getEncoder().encodeToString(fileBytes);
    }
}
