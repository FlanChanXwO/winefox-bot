package com.github.winefoxbot.core.service.helpdoc.impl;

import com.github.winefoxbot.core.init.HelpDocLoader;
import com.github.winefoxbot.core.model.dto.HelpData;
import com.github.winefoxbot.core.model.dto.HelpGroup;
import com.github.winefoxbot.core.service.file.FileStorageService;
import com.github.winefoxbot.core.service.helpdoc.HelpImageService;
import com.github.winefoxbot.core.utils.Base64Utils;
import com.microsoft.playwright.*;
import com.microsoft.playwright.options.ScreenshotType;
import com.microsoft.playwright.options.WaitForSelectorState;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Stream;

/**
 * 帮助图片生成服务的实现类。
 * 适配了资源分离架构 (External Resources + Thin JAR)
 *
 * @author FlanChan
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
    private static final String CACHE_PARENT_DIR = "help";
    private final Lock generateHelpLock = new ReentrantLock();

    @PostConstruct
    private void clearHelpFolder() {
        try {
            fileStorageService.deleteDirectory(CACHE_PARENT_DIR);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public byte[] generateAllHelpImage() {

        String cacheKey = CACHE_PARENT_DIR + "/all_help_image.png";
        byte[] cachedImage = fileStorageService.getFileByCacheKey(cacheKey);
        if (cachedImage != null) {
            return cachedImage;
        }
        generateHelpLock.lock();
        try {
            cachedImage = fileStorageService.getFileByCacheKey(cacheKey);
            if (cachedImage != null) {
                return cachedImage;
            }
            HelpData allHelpData = helpDocLoader.getSortedHelpData();
            byte[] image = renderHelpImage(allHelpData);
            fileStorageService.saveFileByCacheKey(cacheKey, image, Duration.ofDays(1));
            return image;
        } finally {
            generateHelpLock.unlock();
        }
    }

    @Override
    public byte[] generateHelpImageByGroup(String groupName) {
        String normalizedGroupName = groupName.trim().toLowerCase();
        if (normalizedGroupName.isEmpty()) return null;

        String cacheKey = String.format(CACHE_PARENT_DIR + "/group_%s.png", normalizedGroupName);
        byte[] cachedImage = fileStorageService.getFileByCacheKey(cacheKey);
        if (cachedImage != null) return cachedImage;

        HelpData allHelpData = helpDocLoader.getSortedHelpData();
        Optional<HelpGroup> targetGroupOpt = allHelpData.getGroups().stream()
                .filter(g -> g.getName().equalsIgnoreCase(groupName.trim()))
                .findFirst();

        if (targetGroupOpt.isEmpty()) return null;

        generateHelpLock.lock();
        try {
            cachedImage = fileStorageService.getFileByCacheKey(cacheKey);
            if (cachedImage != null) {
                return cachedImage;
            }
            HelpData singleGroupData = new HelpData();
            singleGroupData.setDefaultIcon(allHelpData.getDefaultIcon());
            singleGroupData.setGroups(List.of(targetGroupOpt.get()));

            byte[] image = renderHelpImage(singleGroupData);
            fileStorageService.saveFileByCacheKey(cacheKey, image, Duration.ofDays(1));
            return image;
        } finally {
            generateHelpLock.unlock();
        }
    }
    // ... end of unmodified methods ...

    private byte[] renderHelpImage(HelpData helpData) {
        Context context = new Context();
        context.setVariable("help_data", helpData);
        context.setVariable("hint_text", "具体命令参数请查看详细说明或咨询管理员。");
        Map<String, String> res = loadResourcesAsDataUri(RESOURCE_BASE_PATH);
        context.setVariable("res", res);
        String htmlContent = templateEngine.process(HTML_TEMPLATE, context);
        try (BrowserContext browserContext = browser.newContext(
                new Browser.NewContextOptions()
                        .setDeviceScaleFactor(1))) {
            try (Page page = browserContext.newPage()) {
                page.setContent(htmlContent);
                Locator container = page.locator(".container");
                // 安全获取高度，避免 JS 报错
                Integer heightObj = (Integer) container.evaluate("element => element.scrollHeight");
                int viewportHeight = heightObj != null ? heightObj : 800;

                page.setViewportSize(800, viewportHeight + 10);
                container.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE));
                return container.screenshot(new Locator.ScreenshotOptions().setType(ScreenshotType.PNG));
            }
        }
    }

    /**
     * 【核心修改】双模加载资源：优先扫描外部文件系统，降级扫描 Classpath
     */
    private Map<String, String> loadResourcesAsDataUri(String basePath) {
        Map<String, String> resourceMap = new HashMap<>();

        // 1. 定义外部资源的绝对路径 (对应 application-prod.yml 中的逻辑)
        // 假设运行目录下的 resources/templates/help_report/res
        Path externalPath = Paths.get("resources", basePath);

        if (Files.exists(externalPath) && Files.isDirectory(externalPath)) {
            // === 模式 A：生产环境 (扫描外部文件系统) ===
            log.info("Loading Help resources from External File System: {}", externalPath.toAbsolutePath());
            try (Stream<Path> stream = Files.walk(externalPath)) {
                stream.filter(Files::isRegularFile).forEach(path -> {
                    try {
                        // 计算相对路径作为 Key (例如: "image/bg.png")
                        // Windows 下路径分隔符可能是 \，需要统一替换为 /
                        String key = externalPath.relativize(path).toString().replace("\\", "/");

                        // 读取并转换 Base64
                        String base64 = convertFileToBase64(path);
                        resourceMap.put(key, base64);
                    } catch (Exception e) {
                        log.error("Failed to load external resource: {}", path, e);
                    }
                });
            } catch (IOException e) {
                log.error("Error walking external resource directory: {}", externalPath, e);
            }
        } else {
            // === 模式 B：开发环境 (扫描 Classpath) ===
            log.info("External resources not found at {}. Fallback to Classpath scanning.", externalPath);
            String locationPattern = ResourcePatternResolver.CLASSPATH_ALL_URL_PREFIX + basePath + "/**/*.*";

            try {
                Resource[] resources = resourceResolver.getResources(locationPattern);
                if (resources.length == 0) {
                    log.warn("CRITICAL: No resources found for pattern '{}'.", locationPattern);
                    return resourceMap;
                }

                for (Resource resource : resources) {
                    if (resource.isReadable()) {
                        try {
                            String dataUri = Base64Utils.toBase64String(resource);
                            String fullPath = resource.getURI().toString();

                            // 智能截取 Key
                            int index = fullPath.indexOf(basePath);
                            if (index != -1) {
                                String key = fullPath.substring(index + basePath.length());
                                if (key.startsWith("/")) key = key.substring(1);
                                resourceMap.put(key, dataUri);
                            }
                        } catch (IOException e) {
                            log.error("Failed to load resource: {}", resource.getDescription(), e);
                        }
                    }
                }
            } catch (IOException e) {
                log.error("Could not find resources for pattern: {}.", locationPattern, e);
            }
        }

        if (resourceMap.isEmpty()) {
            log.error("Critical: Resource map is empty. Help images will lack styling/icons.");
        }
        return resourceMap;
    }

    // 辅助方法：处理外部文件的 Base64 转换
    private String convertFileToBase64(Path path) throws IOException {
        byte[] bytes = Files.readAllBytes(path);
        String filename = path.getFileName().toString().toLowerCase();
        // 简单的 MIME 类型判断
        String mimeType = "application/octet-stream";
        if (filename.endsWith(".png")) mimeType = "image/png";
        else if (filename.endsWith(".jpg") || filename.endsWith(".jpeg")) mimeType = "image/jpeg";
        else if (filename.endsWith(".gif")) mimeType = "image/gif";
        else if (filename.endsWith(".css")) mimeType = "text/css";
        else if (filename.endsWith(".js")) mimeType = "application/javascript";
        else if (filename.endsWith(".woff2")) mimeType = "font/woff2"; // 字体文件也很常见

        return "data:" + mimeType + ";base64," + Base64.getEncoder().encodeToString(bytes);
    }
}
