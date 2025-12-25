package com.github.winefoxbot.service.github.impl;


import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.winefoxbot.config.UpdateProperties;
import com.github.winefoxbot.model.dto.github.GitHubRelease;
import com.github.winefoxbot.service.github.GitHubUpdateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

@Service
@Slf4j
@RequiredArgsConstructor
public class GitHubUpdateServiceImpl {

    private final UpdateProperties updateProperties;
    private final OkHttpClient okHttpClient;
    private final ObjectMapper objectMapper;
    private final ApplicationContext context;

    // 一个锁，防止在更新过程中再次触发检查
    private static volatile boolean updateInProgress = false;

    // 使用 @Scheduled 注解来创建定时任务
    // fixedDelayString = "PT1H" 表示每小时检查一次。 "PT10M" 表示每10分钟。
    // initialDelayString = "PT1M" 表示应用启动1分钟后开始第一次检查。
    @Scheduled(initialDelayString = "PT1M", fixedDelayString = "${app.update.check-interval:PT1H}")
    public void checkForUpdate() {
        if (!updateProperties.isEnabled()) {
            log.info("自动更新功能已禁用。");
            return;
        }

        if (updateInProgress) {
            log.warn("更新已在进行中，跳过本次检查。");
            return;
        }

        synchronized (GitHubUpdateService.class) {
            if (updateInProgress) return;
            updateInProgress = true;
        }
        
        try {
            log.info("开始检查应用更新...");

            GitHubRelease latestRelease = fetchLatestRelease();

            if (latestRelease == null || latestRelease.getAssets() == null || latestRelease.getAssets().length == 0) {
                log.error("无法获取有效的 Release 信息或 Release 中不包含任何文件。");
                return;
            }

            long latestReleaseId = latestRelease.getId();
            log.info("检测到最新 Release ID: {}", latestReleaseId);

            // 2. 检查是否需要更新
            long currentReleaseId = getCurrentReleaseId();
            log.info("当前运行的 Release ID: {}", currentReleaseId);

            if (latestReleaseId <= currentReleaseId) {
                log.info("当前已是最新版本，无需更新。");
                return;
            }

            // 3. 执行更新
            log.info("发现新版本，开始执行更新...");
            GitHubRelease.Asset jarAsset = findJarAsset(latestRelease.getAssets());
            if (jarAsset == null) {
                log.error("在 Release 中未找到 .jar 文件。");
                return;
            }

            downloadAndReplace(jarAsset);

            // 4. 更新版本记录文件
            updateVersionFile(latestReleaseId);

            // 5. 重启应用
            restartApplication();

        } catch (Exception e) {
            log.error("检查更新时发生错误: ", e);
        } finally {
            updateInProgress = false;
        }
    }

    private long getCurrentReleaseId() {
        try {
            Path versionFile = Paths.get(updateProperties.getVersionFilePath());
            if (Files.exists(versionFile)) {
                return Long.parseLong(Files.readString(versionFile).trim());
            }
        } catch (IOException | NumberFormatException e) {
            log.warn("读取当前版本文件失败，将视为初始版本。", e);
        }
        return -1; // 表示从未记录过版本
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

    private void downloadAndReplace(GitHubRelease.Asset jarAsset) throws IOException {
        String downloadUrl = jarAsset.getBrowserDownloadUrl();
        String newJarName = jarAsset.getName(); // 新JAR包的文件名
        Path newJarPath = Paths.get(newJarName);
        Path currentJarPath = Paths.get(updateProperties.getCurrentJarName());

        log.info("从 {} 下载新版本...", downloadUrl);
        try (InputStream in = URI.create(downloadUrl).toURL().openStream()) {
            Files.copy(in, newJarPath, StandardCopyOption.REPLACE_EXISTING);
        }
        log.info("下载完成，新文件位于: {}", newJarPath.toAbsolutePath());

        // 替换当前运行的 JAR 文件
        // 注意：在某些操作系统上（如Windows），直接替换正在运行的文件可能会失败。
        // 一个更健壮的方法是下载为 .new，重启时由启动脚本来完成替换。
        // 这里为了简化，我们直接尝试替换。
        log.info("准备将 {} 替换为 {}", currentJarPath, newJarPath);
        Files.move(newJarPath, currentJarPath, StandardCopyOption.REPLACE_EXISTING);
        log.info("文件替换成功！");
    }

    private void updateVersionFile(long newReleaseId) throws IOException {
        Path versionFile = Paths.get(updateProperties.getVersionFilePath());
        Files.writeString(versionFile, String.valueOf(newReleaseId));
        log.info("版本文件已更新为 Release ID: {}", newReleaseId);
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
