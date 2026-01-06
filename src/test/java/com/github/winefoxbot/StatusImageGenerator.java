package com.github.winefoxbot;

import cn.hutool.core.util.RandomUtil;
import com.google.common.reflect.ClassPath;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.options.WaitForSelectorState;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.thymeleaf.ThymeleafAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.StringTemplateResolver;
import oshi.SystemInfo;
import oshi.hardware.CentralProcessor;
import oshi.hardware.GlobalMemory;
import oshi.hardware.HardwareAbstractionLayer;
import oshi.hardware.NetworkIF;
import oshi.software.os.OperatingSystem;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.DecimalFormat;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@SpringBootTest(classes = {ThymeleafAutoConfiguration.class})
public class StatusImageGenerator {

    private static final DecimalFormat df = new DecimalFormat("0.00");
    private @Autowired TemplateEngine templateEngine;

    @Test
    public void test() {
        try {
            // 1. 获取动态系统数据 (与之前完全相同)
            SystemInfo si = new SystemInfo();
            HardwareAbstractionLayer hal = si.getHardware();
            OperatingSystem os = si.getOperatingSystem();
            CentralProcessor processor = hal.getProcessor();
            GlobalMemory memory = hal.getMemory();
            // 2. 将所有动态数据存入一个 Map (作为数据模型)
            Map<String, Object> dataModel = new HashMap<>();
            long[] prevTicks = processor.getSystemCpuLoadTicks();
            // hal.getNetworkIFs(true); // 可以在采样前强制更新一次，确保数据是新的
            List<NetworkIF> initialNetList = hal.getNetworkIFs();
            long totalBytesRecvInitial = initialNetList.stream().mapToLong(NetworkIF::getBytesRecv).sum();
            long totalBytesSentInitial = initialNetList.stream().mapToLong(NetworkIF::getBytesSent).sum();

            // 2. 统一等待1秒
            TimeUnit.SECONDS.sleep(1);

            // 3. 进行第二次采样并计算结果
            // 计算 CPU 使用率
            double cpuLoad = processor.getSystemCpuLoadBetweenTicks(prevTicks) * 100;
            dataModel.put("cpuUsage", cpuLoad); // 放入模型

            // 计算网络速率
            hal.getNetworkIFs(true); // 强制更新以获取最新数据
            List<NetworkIF> finalNetList = hal.getNetworkIFs();
            long totalBytesRecvFinal = finalNetList.stream().mapToLong(NetworkIF::getBytesRecv).sum();
            long totalBytesSentFinal = finalNetList.stream().mapToLong(NetworkIF::getBytesSent).sum();

            double downloadSpeedKBps = (totalBytesRecvFinal - totalBytesRecvInitial) / 1024.0;
            double uploadSpeedKBps = (totalBytesSentFinal - totalBytesSentInitial) / 1024.0;
            dataModel.put("downloadSpeed", String.format("%.1f", downloadSpeedKBps));
            dataModel.put("uploadSpeed", String.format("%.1f", uploadSpeedKBps));

        // 计算系统负载百分比 (这段逻辑依赖于 cpuUsage，所以放在后面)
            int logicalProcessorCount = processor.getLogicalProcessorCount();
            double systemLoad = processor.getSystemLoadAverage(1)[0];
            double loadPercentage;
            if (systemLoad < 0) {
                loadPercentage = cpuLoad; // 直接使用刚刚计算好的 cpuLoad
            } else {
                loadPercentage = (systemLoad / logicalProcessorCount) * 100.0;
            }
            loadPercentage = Math.min(loadPercentage, 100.0);
            dataModel.put("loadPercentage", loadPercentage);

            dataModel.put("cpuFrequency", df.format(processor.getMaxFreq() / 1e9));
            dataModel.put("cpuCores", processor.getLogicalProcessorCount());

            double ramUsed = (memory.getTotal() - memory.getAvailable()) / 1e9;
            double ramTotal = memory.getTotal() / 1e9;
            dataModel.put("ramUsed", ramUsed);
            dataModel.put("ramTotal", ramTotal);

            double swapUsed = memory.getVirtualMemory().getSwapUsed() / 1e9;
            double swapTotal = memory.getVirtualMemory().getSwapTotal() / 1e9;
            dataModel.put("swapUsed", swapUsed);
            dataModel.put("swapTotal", swapTotal);

            long diskTotalBytes = os.getFileSystem().getFileStores().stream().mapToLong(fs -> fs.getTotalSpace()).sum();
            long diskUsableBytes = os.getFileSystem().getFileStores().stream().mapToLong(fs -> fs.getUsableSpace()).sum();
            double diskUsed = (diskTotalBytes - diskUsableBytes) / 1e9;
            double diskTotal = diskTotalBytes / 1e9;
            dataModel.put("diskUsed", diskUsed);
            dataModel.put("diskTotal", diskTotal);

            dataModel.put("cpuName", toSystemInfoEllipsis(processor.getProcessorIdentifier().getName()));
            dataModel.put("osName", toSystemInfoEllipsis(os.toString()));
            dataModel.put("botName", toNickNameEllipsis("WineFoxBot"));
            dataModel.put("dashboardName", toNickNameEllipsis("WineFoxBot"));
            dataModel.put("projectVersion", null);
            long uptimeSeconds = os.getSystemUptime();
            dataModel.put("uptime", String.format("%d 小时 %d 分", TimeUnit.SECONDS.toHours(uptimeSeconds), TimeUnit.SECONDS.toMinutes(uptimeSeconds) % 60));

            // 放入数据模型
            dataModel.put("downloadSpeed", String.format("%.1f", downloadSpeedKBps));
            dataModel.put("uploadSpeed", String.format("%.1f", uploadSpeedKBps));

            // 3. 使用 Thymeleaf 渲染 HTML
            // 3.1 初始化 Thymeleaf 引擎
            StringTemplateResolver templateResolver = new StringTemplateResolver();
            templateResolver.setTemplateMode(TemplateMode.HTML);
            templateEngine.setTemplateResolver(templateResolver);

            // 3.2 创建 Thymeleaf 上下文并填充数据
            Context context = new Context();
            context.setVariables(dataModel);

            // 3.3 读取模板并进行渲染
            String htmlTemplate = readResourceFile("templates/status/main.html");
            String renderedHtml = templateEngine.process(htmlTemplate, context);
            String cssContent = readResourceFile("templates/status/res/css/style.css");

            // 随机选择一个 banner 图片，并将其转换为 Base64
            String randomBannerPath = getRandomImageFromResources("templates/status/res/image/banner");
            if (randomBannerPath != null) {
                // 根据文件名后缀判断 MIME 类型
                String mimeType = randomBannerPath.endsWith(".png") ? "image/png" : "image/jpeg";
                String topBannerBase64 = resourceToBase64(randomBannerPath, mimeType);
                cssContent = cssContent.replace("${topBannerImage}", topBannerBase64);
            } else {
                // 可以设置一个默认值或抛出异常
                cssContent = cssContent.replace("${topBannerImage}", ""); // 或者一个默认的Base64图片
                System.err.println("警告: 在 banner 目录中未找到任何图片!");
            }
            // 随机选择一个 banner 图片，并将其转换为 Base64
            String characterPath = getRandomImageFromResources("templates/status/res/image/character");
            if (characterPath != null) {
                // 根据文件名后缀判断 MIME 类型
                String mimeType = characterPath.endsWith(".png") ? "image/png" : "image/jpeg";
                String characterBase64 = resourceToBase64(characterPath, mimeType);
                cssContent = cssContent.replace("${characterImage}", characterBase64);
            } else {
                // 可以设置一个默认值或抛出异常
                cssContent = cssContent.replace("${characterImage}", ""); // 或者一个默认的Base64图片
                System.err.println("警告: 在 characterPath 目录中未找到任何图片!");
            }

            String finalHtml = renderedHtml.replace("${cssStyle}", "<style>" + cssContent + "</style>");


            // 5. 使用 Playwright 截图 (与之前完全相同)
            try (Playwright playwright = Playwright.create()) {
                Browser browser = playwright.chromium().launch();
                Page page = browser.newPage();
                page.setContent(finalHtml);

                Locator cardElement = page.locator(".card");
                cardElement.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE));
                Path outputPath = Paths.get("bot-status-thymeleaf.png");
                cardElement.screenshot(new Locator.ScreenshotOptions()
                        .setPath(outputPath)
                        .setOmitBackground(true)); // 背景透明

                System.out.println("成功使用 Thymeleaf 生成状态图片: " + outputPath.toAbsolutePath());
                browser.close();
            }

        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
    }

    /**
     * 从指定的资源目录中随机获取一个图片文件的路径。
     * @param directoryPath 资源目录路径，例如 "templates/status/res/image/banner"
     * @return 随机找到的资源文件的完整路径，如果目录为空或不存在则返回 null。
     * @throws IOException 如果扫描资源时发生错误
     */
    private String getRandomImageFromResources(String directoryPath) throws IOException {
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        // 使用 Guava 的 ClassPath 工具扫描指定包（目录）下的所有资源
        List<String> imagePaths = ClassPath.from(loader)
                .getResources()
                .stream()
                // 获取资源的完整路径名 (e.g., "templates/status/res/image/banner/top-banner_1.png")
                .map(ClassPath.ResourceInfo::getResourceName)
                // 【关键修改】直接使用 / 分隔的路径进行前缀匹配
                .filter(resourceName -> resourceName.startsWith(directoryPath + "/"))
                // 进一步过滤，确保是图片文件
                .filter(resourceName -> resourceName.matches(".*\\.(png|jpg|jpeg|gif)$"))
                .toList();

        if (imagePaths.isEmpty()) {
            return null;
        }

        // 随机选择一个资源
        return imagePaths.get(RandomUtil.randomInt(imagePaths.size()));
    }

    /**
     * 从资源文件夹读取文件内容为字符串
     */
    private String readResourceFile(String path) throws IOException {
        try (InputStream is = StatusImageGenerator.class.getClassLoader().getResourceAsStream(path)) {
            if (is == null) throw new IOException("Cannot find resource: " + path);
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    /**
     * 将资源文件转换为 Base64 Data URL
     */
    private String resourceToBase64(String resourcePath, String mimeType) throws IOException {
        try (InputStream is = StatusImageGenerator.class.getClassLoader().getResourceAsStream(resourcePath)) {
            if (is == null) throw new IOException("Cannot find resource: " + resourcePath);
            byte[] fileContent = is.readAllBytes();
            return "data:" + mimeType + ";base64," + Base64.getEncoder().encodeToString(fileContent);
        }
    }

    private String toNickNameEllipsis(String str) {
        int maxLength = 10;
        if (str.length() <= maxLength) {
            return str;
        }
        return str.substring(0, maxLength) + "...";
    }


    private String toSystemInfoEllipsis(String str) {
        int maxLength = 30;
        if (str.length() <= maxLength) {
            return str;
        }
        return str.substring(0, maxLength) + "...";
    }
}
