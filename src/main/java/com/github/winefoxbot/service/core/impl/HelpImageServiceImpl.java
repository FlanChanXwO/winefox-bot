package com.github.winefoxbot.service.core.impl;

import com.github.winefoxbot.init.HelpDocLoader;
import com.github.winefoxbot.model.dto.helpdoc.HelpData;
import com.github.winefoxbot.model.dto.helpdoc.HelpGroup;
import com.github.winefoxbot.service.core.HelpImageService;
import com.github.winefoxbot.service.file.FileStorageService;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.ScreenshotType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.io.IOException;
import java.time.Duration;
import java.util.*;

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
    private final Browser browser;
    private final FileStorageService fileStorageService;
    private static final String HTML_TEMPLATE = "help_report/main";
    private static final String RESOURCE_BASE_PATH = "templates/help_report/res";

    @Override
    public byte[] generateAllHelpImage(){
        String cacheKey = "help/all_help_image.png"; // 使用层级结构，更清晰
        byte[] cachedImage = fileStorageService.getFileByCacheKey(cacheKey);
        if (cachedImage != null) {
            log.info("Cache hit for all help image using key: {}", cacheKey);
            return cachedImage;
        }

        log.info("Cache miss for all help image. Generating new one...");
        // 1. 获取所有分组的数据
        HelpData allHelpData = helpDocLoader.getSortedHelpData();

        // 2. 渲染图片
        byte[] image = renderHelpImage(allHelpData);

        // 3. 缓存图片
        fileStorageService.saveFileByCacheKey(cacheKey, image, Duration.ofDays(1));
        log.info("All help image has been generated and cached with key: {}", cacheKey);

        return image;
    }

    @Override
    public byte[] generateHelpImageByGroup(String groupName){
        // 对 groupName 进行规范化处理，以用作缓存键的一部分
        String normalizedGroupName = groupName.trim().toLowerCase();
        if (normalizedGroupName.isEmpty()) {
            log.warn("Group name cannot be empty.");
            return null;
        }

        // 根据规范化的分组名生成唯一的缓存键
        String cacheKey = String.format("help/group_%s.png", normalizedGroupName);

        byte[] cachedImage = fileStorageService.getFileByCacheKey(cacheKey);
        if (cachedImage != null) {
            log.info("Cache hit for group '{}' help image using key: {}", groupName, cacheKey);
            return cachedImage;
        }

        log.info("Cache miss for group '{}' help image. Generating new one...", groupName);
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

        // 4. 渲染图片
        byte[] image = renderHelpImage(singleGroupData);

        // 5. 缓存图片
        fileStorageService.saveFileByCacheKey(cacheKey, image, Duration.ofDays(1));
        log.info("Help image for group '{}' has been generated and cached with key: {}", groupName, cacheKey);

        return image;
    }

    private byte[] renderHelpImage(HelpData helpData) {
        Context context = new Context();
        context.setVariable("help_data", helpData);
        context.setVariable("hint_text", "具体命令参数请查看详细说明或咨询管理员。");
        Map<String, String> res = loadResourcesAsDataUri(RESOURCE_BASE_PATH);
        context.setVariable("res", res);
        String htmlContent = templateEngine.process(HTML_TEMPLATE, context);
        try (BrowserContext browserContext = browser.newContext(
                new Browser.NewContextOptions().setDeviceScaleFactor(1))) {
            try (Page page = browserContext.newPage()) {
                // 直接使用完全渲染好的HTML
                page.setContent(htmlContent);
                Locator container = page.locator(".container");
                return container.screenshot(new Locator.ScreenshotOptions().setType(ScreenshotType.PNG));
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
                            String fullPath = resource.getURI().toString();
                            // 找到 basePath 在完整路径中的位置，然后取其后的部分
                            String relativePath = fullPath.substring(fullPath.indexOf(basePath) + basePath.length() + 1);
                            String key = relativePath;
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
