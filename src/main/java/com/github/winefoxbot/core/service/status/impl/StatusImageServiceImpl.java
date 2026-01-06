package com.github.winefoxbot.core.service.status.impl;

import com.github.winefoxbot.core.config.status.StatusImageGeneratorConfig;
import com.github.winefoxbot.core.service.status.StatusImageService;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.ScreenshotType;
import com.microsoft.playwright.options.WaitForSelectorState;
import com.mikuac.shiro.core.PluginManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.time.DurationFormatUtils;
import org.springframework.context.ApplicationContext;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import oshi.SystemInfo;
import oshi.hardware.CentralProcessor;
import oshi.hardware.GlobalMemory;
import oshi.hardware.HardwareAbstractionLayer;
import oshi.hardware.NetworkIF;
import oshi.software.os.OperatingSystem;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.text.DecimalFormat;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class StatusImageServiceImpl implements StatusImageService {

    private static final String CLASSPATH_PREFIX = "classpath:";
    private static final DecimalFormat DF = new DecimalFormat("0.00");

    private final TemplateEngine templateEngine;
    private final ResourcePatternResolver resourceResolver;
    private final Browser browser;
    private final StatusImageGeneratorConfig config;
    // 用于获取插件数量
    private final PluginManager pluginManager;
    // 用于获取运行时间
    private final ApplicationContext applicationContext;

    @Override
    public byte[] generateStatusImage() throws IOException, InterruptedException {
        // 1. 获取系统动态数据
        Map<String, Object> dataModel = gatherSystemInfo();

        // 2. 渲染 HTML
        String finalHtml = renderHtmlTemplate(dataModel);

        // 3. 使用 Playwright 截图
        return captureScreenshot(finalHtml);
    }

    private Map<String, Object> gatherSystemInfo() throws InterruptedException {
        SystemInfo si = new SystemInfo();
        HardwareAbstractionLayer hal = si.getHardware();
        OperatingSystem os = si.getOperatingSystem();
        CentralProcessor processor = hal.getProcessor();
        GlobalMemory memory = hal.getMemory();
        
        Map<String, Object> dataModel = new HashMap<>();

        // 第一次采样
        long[] prevTicks = processor.getSystemCpuLoadTicks();
        List<NetworkIF> initialNetList = hal.getNetworkIFs();
        long totalBytesRecvInitial = initialNetList.stream().mapToLong(NetworkIF::getBytesRecv).sum();
        long totalBytesSentInitial = initialNetList.stream().mapToLong(NetworkIF::getBytesSent).sum();

        // 等待1秒
        TimeUnit.SECONDS.sleep(1);

        // 第二次采样并计算
        double cpuLoad = processor.getSystemCpuLoadBetweenTicks(prevTicks) * 100;
        hal.getNetworkIFs(true); // 强制更新
        List<NetworkIF> finalNetList = hal.getNetworkIFs();
        long totalBytesRecvFinal = finalNetList.stream().mapToLong(NetworkIF::getBytesRecv).sum();
        long totalBytesSentFinal = finalNetList.stream().mapToLong(NetworkIF::getBytesSent).sum();

        double downloadSpeedKBps = (totalBytesRecvFinal - totalBytesRecvInitial) / 1024.0;
        double uploadSpeedKBps = (totalBytesSentFinal - totalBytesSentInitial) / 1024.0;
        
        int logicalProcessorCount = processor.getLogicalProcessorCount();
        double systemLoad = processor.getSystemLoadAverage(1)[0];
        double loadPercentage = (systemLoad < 0) ? cpuLoad : Math.min((systemLoad / logicalProcessorCount) * 100.0, 100.0);

        dataModel.put("cpuUsage", cpuLoad);
        dataModel.put("loadPercentage", loadPercentage);
        dataModel.put("downloadSpeed", String.format("%.1f", downloadSpeedKBps));
        dataModel.put("uploadSpeed", String.format("%.1f", uploadSpeedKBps));

        dataModel.put("cpuFrequency", DF.format(processor.getMaxFreq() / 1e9));
        dataModel.put("cpuCores", processor.getLogicalProcessorCount());

        dataModel.put("ramUsed", (memory.getTotal() - memory.getAvailable()) / 1e9);
        dataModel.put("ramTotal", memory.getTotal() / 1e9);

        dataModel.put("swapUsed", memory.getVirtualMemory().getSwapUsed() / 1e9);
        dataModel.put("swapTotal", memory.getVirtualMemory().getSwapTotal() / 1e9);

        long diskTotalBytes = os.getFileSystem().getFileStores().stream().mapToLong(fs -> fs.getTotalSpace()).sum();
        long diskUsableBytes = os.getFileSystem().getFileStores().stream().mapToLong(fs -> fs.getUsableSpace()).sum();
        dataModel.put("diskUsed", (diskTotalBytes - diskUsableBytes) / 1e9);
        dataModel.put("diskTotal", diskTotalBytes / 1e9);

        dataModel.put("cpuName", toSystemInfoEllipsis(processor.getProcessorIdentifier().getName()));
        dataModel.put("osName", toSystemInfoEllipsis(os.toString()));

        long startupTimestamp = applicationContext.getStartupDate();
        long uptimeMillis = System.currentTimeMillis() - startupTimestamp;
        String formattedUptime = DurationFormatUtils.formatDuration(uptimeMillis, "d' 天 'H' 小时 'm' 分'");

        // 如果想让 0 天不显示，可以这样做：
        if (uptimeMillis < 86400000) { // 小于一天的毫秒数
            formattedUptime = DurationFormatUtils.formatDuration(uptimeMillis, "H' 小时 'm' 分'");
        }

        dataModel.put("uptime", formattedUptime);
        URLClassLoader pluginClassLoader = pluginManager.getPluginClassLoader();
        dataModel.put("pluginCount", pluginClassLoader != null ? pluginClassLoader.getURLs().length : 0);

        // 从配置中获取
        dataModel.put("botName", toNickNameEllipsis(config.getBotName()));
        dataModel.put("dashboardName", toNickNameEllipsis(config.getDashboardName()));
        dataModel.put("projectVersion", config.getProjectName() + " " + config.getProjectVersion());
        return dataModel;
    }

    private String renderHtmlTemplate(Map<String, Object> dataModel) throws IOException {
        Context context = new Context();
        context.setVariables(dataModel);
        String renderedHtml = templateEngine.process(config.getHtmlTemplatePath(), context);

        // 后续逻辑不变
        String cssContent = readResourceContent(config.getCssTemplatePath());

        String characterImageBase64 = imagePathToBase64(config.getCharacterImagePath());
        String topBannerImageBase64 = imagePathToBase64(config.getTopBannerImagePath());

        cssContent = cssContent.replace("${characterImage}", characterImageBase64);
        cssContent = cssContent.replace("${topBannerImage}", topBannerImageBase64);

        return renderedHtml.replace("${cssStyle}", "<style>" + cssContent + "</style>");
    }

    private byte[] captureScreenshot(String htmlContent) {
        try (Page page = browser.newPage()) {
            page.setContent(htmlContent);
            Locator cardElement = page.locator(".card");
            cardElement.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE));
            return cardElement.screenshot(new Locator.ScreenshotOptions()
                    .setType(ScreenshotType.PNG)
                    .setOmitBackground(true));
        }
    }

    private String imagePathToBase64(String imagePath) {
        if (imagePath == null || imagePath.isBlank()) {
            log.warn("图片路径未配置，将使用默认透明图片。");
            return config.getDefaultImageBase64();
        }
        
        try {
            if (imagePath.startsWith(CLASSPATH_PREFIX)) {
                Resource resource = resourceResolver.getResource(imagePath);
                if (!resource.exists()) {
                    log.error("图片资源未找到: {}", imagePath);
                    return config.getDefaultImageBase64();
                }

                try (InputStream is = resource.getInputStream()) {
                    byte[] fileContent = is.readAllBytes();
                    String mimeType = getMimeType(imagePath);
                    return "data:" + mimeType + ";base64," + Base64.getEncoder().encodeToString(fileContent);
                }
            } else {
                File file = new File(imagePath);
                if (!file.exists()) {
                    log.error("图片资源未找到: {}", imagePath);
                    return config.getDefaultImageBase64();
                }

                try (InputStream is = Files.newInputStream(file.toPath())) {
                    byte[] fileContent = is.readAllBytes();
                    String mimeType = getMimeType(imagePath);
                    return "data:" + mimeType + ";base64," + Base64.getEncoder().encodeToString(fileContent);
                }
            }
        } catch (IOException e) {
            log.error("读取图片资源失败: {}", imagePath, e);
            return config.getDefaultImageBase64(); // 出错时返回默认值
        }
    }

    private String readResourceContent(String path) throws IOException {
            if (path.startsWith(CLASSPATH_PREFIX)) {
                Resource resource = resourceResolver.getResource(path);
                if (!resource.exists()) {
                    throw new IOException("无法找到模板资源: " + path);
                }
                try (InputStream is = resource.getInputStream()) {
                    return new String(is.readAllBytes(), StandardCharsets.UTF_8);
                }
            } else {
                File file = new File(path);
                if (!file.exists()) {
                    throw new IOException("无法找到模板资源: " + path);
                }
                try (InputStream is = Files.newInputStream(file.toPath())) {
                    return new String(is.readAllBytes(), StandardCharsets.UTF_8);
                }
            }
    }

    
    private String getMimeType(String path) {
        if (path.endsWith(".png")) return "image/png";
        if (path.endsWith(".jpg") || path.endsWith(".jpeg")) return "image/jpeg";
        if (path.endsWith(".gif")) return "image/gif";
        if (path.endsWith(".webp")) return "image/webp";
        return "application/octet-stream"; // 默认MIME类型
    }

    private String toNickNameEllipsis(String str) {
        return str.length() > 10 ? str.substring(0, 10) + "..." : str;
    }

    private String toSystemInfoEllipsis(String str) {
        return str.length() > 30 ? str.substring(0, 30) + "..." : str;
    }

}
