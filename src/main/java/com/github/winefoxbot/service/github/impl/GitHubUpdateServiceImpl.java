package com.github.winefoxbot.service.github.impl;


import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.winefoxbot.config.UpdateProperties;
import com.github.winefoxbot.model.dto.github.GitHubRelease;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.PropertiesLoaderUtils;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Properties;

@Service
@Slf4j
@RequiredArgsConstructor
public class GitHubUpdateServiceImpl {

    private final UpdateProperties updateProperties;
    private final OkHttpClient okHttpClient;
    private final ObjectMapper objectMapper;
    private final ApplicationContext context;


    // 这个变量用来缓存当前应用的版本ID，避免每次都读取文件
    private long currentReleaseId = -1;

    // 一个锁，防止在更新过程中再次触发检查
    private static volatile boolean updateInProgress = false;

    // 用一个简单的内部类来缓存版本信息
    private static class VersionInfo {
        long releaseId = -1;
        long assetId = -1;
    }
    private VersionInfo currentVersionInfo = null;

    // 使用 @Scheduled 注解来创建定时任务
    // fixedDelayString = "PT1H" 表示每小时检查一次。 "PT10M" 表示每10分钟。
    // initialDelayString = "PT1M" 表示应用启动1分钟后开始第一次检查。
    @Scheduled(initialDelayString = "PT1M", fixedDelayString = "${app.update.check-interval:PT1H}")
    public void checkForUpdate() {
        // ... (前面的 enabled 和 inProgress 检查不变) ...
        try {
            log.info("开始检查应用更新...");

            // 1. 获取 GitHub 最新 Release 信息
            GitHubRelease latestRelease = fetchLatestRelease();
            GitHubRelease.Asset jarAsset = findJarAsset(latestRelease.getAssets());
            if (jarAsset == null) {
                log.error("在 Release 中未找到 .jar 文件。");
                return;
            }
            long latestReleaseId = latestRelease.getId();
            long latestAssetId = jarAsset.getId(); // 获取最新资产的ID
            log.info("检测到最新版本 - Release ID: {}, Asset ID: {}", latestReleaseId, latestAssetId);

            // 2. 检查是否需要更新（核心逻辑修改）
            VersionInfo currentVersion = getCurrentVersionInfo();
            log.info("当前运行版本 - Release ID: {}, Asset ID: {}", currentVersion.releaseId, currentVersion.assetId);

            // 关键判断：当 Release ID 相同，但 Asset ID 不同时，也需要更新！
            if (latestReleaseId == currentVersion.releaseId && latestAssetId == currentVersion.assetId) {
                log.info("当前已是最新版本，无需更新。");
                return;
            }

            if (latestReleaseId < currentVersion.releaseId) {
                log.warn("检测到的线上版本 (Release ID: {}) 比当前版本 (Release ID: {}) 更旧，跳过更新。", latestReleaseId, currentVersion.releaseId);
                return;
            }

            // 3. 执行更新
            log.info("发现新版本或文件更新，开始执行...");
            downloadAndReplace(jarAsset); // 使用已获取的 jarAsset 对象

            // 4. 重启应用 (不再需要更新版本文件)
            restartApplication();

        } catch (Exception e) {
            log.error("检查更新时发生错误: ", e);
        } finally {
            updateInProgress = false;
        }
    }

    /**
     * 从 JAR 包内部的 release-info.properties 文件中获取当前 Release ID。
     * 结果会被缓存。
     * @return 当前应用的 Release ID
     */
    private long getCurrentReleaseId() {
        if (this.currentReleaseId != -1) {
            return this.currentReleaseId;
        }

        try {
            // 使用 Spring 的 ClassPathResource 来安全地读取 classpath 内的资源
            ClassPathResource resource = new ClassPathResource("release-info.properties");
            Properties props = PropertiesLoaderUtils.loadProperties(resource);
            String releaseIdStr = props.getProperty("github.release.id");
            this.currentReleaseId = Long.parseLong(releaseIdStr.trim());
        } catch (IOException | NumberFormatException | NullPointerException e) {
            log.warn("无法从 'release-info.properties' 文件中读取有效的 Release ID。将使用默认值-1。", e);
            this.currentReleaseId = -1; // 出错时返回默认值
        }
        return this.currentReleaseId;
    }

    private VersionInfo getCurrentVersionInfo() {
        if (this.currentVersionInfo != null) {
            return this.currentVersionInfo;
        }

        VersionInfo info = new VersionInfo();
        try {
            ClassPathResource resource = new ClassPathResource("release-info.properties");
            Properties props = PropertiesLoaderUtils.loadProperties(resource);
            info.releaseId = Long.parseLong(props.getProperty("github.release.id", "-1").trim());
            info.assetId = Long.parseLong(props.getProperty("github.asset.id", "-1").trim());
        } catch (IOException | NumberFormatException e) {
            log.warn("无法从 'release-info.properties' 文件中读取有效的版本信息。将使用默认值-1。", e);
        }
        this.currentVersionInfo = info;
        return this.currentVersionInfo;
    }

    private void downloadAndReplace(GitHubRelease.Asset jarAsset) throws IOException {
        String downloadUrl = jarAsset.getBrowserDownloadUrl();
        Path newJarPath = Paths.get(jarAsset.getName() + ".new");
        Path currentJarPath = Paths.get(updateProperties.getCurrentJarName());

        log.info("从 {} 下载新版本...", downloadUrl);
        // ... (下载逻辑与之前完全相同) ...
        Request request = new Request.Builder().url(downloadUrl).build();
        try (Response response = okHttpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) throw new IOException("下载文件失败: " + response);
            ResponseBody body = response.body();
            if (body == null) throw new IOException("下载的文件响应体为空");
            try (InputStream in = body.byteStream()) {
                Files.copy(in, newJarPath, StandardCopyOption.REPLACE_EXISTING);
            }
        }
        log.info("下载完成，新文件位于: {}", newJarPath.toAbsolutePath());

        log.info("准备将 {} 替换为 {}", newJarPath.toAbsolutePath(), currentJarPath.toAbsolutePath());
        Files.move(newJarPath, currentJarPath, StandardCopyOption.REPLACE_EXISTING);
        log.info("文件替换成功！");

        // 注意：不再需要调用 updateVersionFile 方法了！
    }
    private GitHubRelease fetchLatestRelease() throws IOException {
        final String apiUrl = updateProperties.getGithubApiUrl()
                .replace("{repo}", updateProperties.getGithubRepo())
                .replace("{tag}", updateProperties.getReleaseTag());

        Request request = new Request.Builder()
                .url(apiUrl)
                // 添加 User-Agent, GitHub API 要求
                .header("User-Agent", "Java-App-Updater")
                .build();

        try (Response response = okHttpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("请求 GitHub API 失败: " + response);
            }
            ResponseBody body = response.body();
            if (body == null) {
                throw new IOException("GitHub API 响应体为空");
            }
            // 使用 ObjectMapper 将 JSON 字符串解析为 Java 对象
            return objectMapper.readValue(body.string(), GitHubRelease.class);
        }
    }

    private GitHubRelease.Asset findJarAsset(GitHubRelease.Asset[] assets) {
        for (GitHubRelease.Asset asset : assets) {
            if (asset.getName().endsWith(".jar")) {
                return asset;
            }
        }
        return null;
    }
    
    private void restartApplication() {
        log.info("准备重启应用...");

        // 获取当前应用的线程
        Thread restartThread = new Thread(() -> {
            try {
                // 等待一小段时间，确保日志等操作完成
                Thread.sleep(1000); 
                // 关闭当前应用上下文，触发应用的优雅停机
                SpringApplication.exit(context, () -> 0);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.error("重启线程被中断", e);
            }
        });

        // 设置为守护线程，这样主应用退出时它也会退出
        restartThread.setDaemon(false);
        restartThread.start();
    }
}
