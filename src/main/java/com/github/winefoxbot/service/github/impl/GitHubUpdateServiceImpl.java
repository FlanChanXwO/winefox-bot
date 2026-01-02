package com.github.winefoxbot.service.github.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.winefoxbot.config.app.WineFoxBotAppUpdateProperties;
import com.github.winefoxbot.model.dto.core.RestartInfo;
import com.github.winefoxbot.model.dto.github.GitHubRelease;
import com.github.winefoxbot.service.github.GitHubUpdateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationContext;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.PropertiesLoaderUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

@Service
@Slf4j
@RequiredArgsConstructor
public class GitHubUpdateServiceImpl implements GitHubUpdateService {

    private final WineFoxBotAppUpdateProperties updateProperties;
    private final OkHttpClient okHttpClient;
    private final ObjectMapper objectMapper;
    private final ApplicationContext context;
    // 使用 AtomicReference 来安全地持有 VersionInfo
    private final AtomicReference<VersionInfo> currentVersionRef = new AtomicReference<>();
    // 使用 CompletableFuture 来处理异步初始化
    private final CompletableFuture<VersionInfo> versionInfoFuture = new CompletableFuture<>();
    private VersionInfo currentVersionInfo = null;
    private static final AtomicBoolean updateInProgress = new AtomicBoolean(false);

    private static final String RESTART_INFO_FILE = "restart-info.json";
    // 定义一个特殊的退出码，用于告知外部脚本这是计划内的重启
    private static final int RESTART_EXIT_CODE = 5;

    @EventListener(ApplicationReadyEvent.class)
    public void initializeVersionInfoOnStartup() {
        log.info("Application is ready. Starting to fetch current version info from GitHub...");
        try {
            GitHubRelease latestRelease = fetchLatestRelease();
            VersionInfo info = new VersionInfo();
            info.releaseId = latestRelease.getId();
            info.name = latestRelease.getTagName();
            info.tagName = latestRelease.getTagName();

            GitHubRelease.Asset jarAsset = findJarAsset(latestRelease.getAssets());
            if (jarAsset != null) {
                info.assetId = jarAsset.getId();
            } else {
                log.warn("No JAR asset found in the latest release '{}'. Asset ID will be -1.", latestRelease.getTagName());
                info.assetId = -1;
            }

            // 安全地设置版本信息
            currentVersionRef.set(info);
            // 通知所有等待者，版本信息已经准备好了
            versionInfoFuture.complete(info);
            log.info("Successfully fetched and set current version info: {}", info);

        } catch (Exception e) {
            log.error("Failed to fetch latest version info from GitHub on startup.", e);
            VersionInfo errorInfo = createDefaultVersion("获取失败");
            currentVersionRef.set(errorInfo);
            // 通知所有等待者，初始化出错了
            versionInfoFuture.completeExceptionally(e);
        }
    }

    @Override
    public void performUpdate() throws Exception {
        if (!updateInProgress.compareAndSet(false, true)) {
            throw new IllegalStateException("更新已经在进行中，请勿重复操作。");
        }
        try {
            log.info("开始检查应用更新...");
            GitHubRelease latestRelease = fetchLatestRelease();
            GitHubRelease.Asset jarAsset = findJarAsset(latestRelease.getAssets());
            if (jarAsset == null) {
                throw new IllegalStateException("在 Release '" + latestRelease.getTagName() + "' 中未找到 .jar 文件。");
            }

            // 【修改】直接使用联网获取的最新信息
            VersionInfo latestVersion = new VersionInfo();
            latestVersion.releaseId = latestRelease.getId();
            latestVersion.assetId = jarAsset.getId();
            log.info("检测到最新版本 - Release ID: {}, Asset ID: {}", latestVersion.releaseId, latestVersion.assetId);

            // 【修改】获取当前运行版本信息的方式也变了
            VersionInfo currentVersion = getCurrentVersionInfo();
            log.info("当前运行版本 - Release ID: {}, Asset ID: {}", currentVersion.releaseId, currentVersion.assetId);

            if (latestVersion.releaseId == currentVersion.releaseId) {
                throw new IllegalStateException("当前已是最新版本，无需更新。");
            }
            if (latestVersion.releaseId < currentVersion.releaseId) {
                throw new IllegalStateException("检测到的线上版本比当前版本更旧，跳过更新。");
            }

            log.info("发现新版本，开始下载并替换...");
            downloadAndReplace(jarAsset);

            log.info("文件更新完成，即将重启应用...");
            restartApplication();

        } finally {
            updateInProgress.set(false);
        }
    }

    @Override
    public VersionInfo getCurrentVersionInfo() {
        // 尝试直接从缓存获取，如果已经初始化完成，这是最快的路径
        if (versionInfoFuture.isDone() && !versionInfoFuture.isCompletedExceptionally()) {
            return currentVersionRef.get();
        }

        // 如果初始化还未完成，或者已完成但出错了，则等待
        try {
            log.warn("Version info is not ready yet, waiting for initialization... (max 10 seconds)");
            // 等待最多10秒，等待 initializeVersionInfoOnStartup() 完成
            return versionInfoFuture.get(10, TimeUnit.SECONDS);
        } catch (InterruptedException | ExecutionException | TimeoutException e) {
            log.error("Could not get version info within the time limit or an error occurred.", e);
            // 如果等待超时或初始化失败，返回一个清晰的错误对象
            return createDefaultVersion("等待超时或初始化失败");
        }
    }

    @Override
    public GitHubRelease fetchLatestRelease() throws IOException {
        final String apiUrl = updateProperties.getGithubApiUrl()
                .replace("{repo}", updateProperties.getGithubRepo())
                .replace("{tag}", updateProperties.getReleaseTag());

        Request.Builder requestBuilder = new Request.Builder()
                .url(apiUrl)
                .header("Accept", "application/vnd.github.v3+json") // 推荐加上，明确 API 版本
                .header("User-Agent", "WineFox-Bot-Updater");

        // 从配置中获取 token
        String token = updateProperties.getGithubToken();

        // 检查 token 是否存在且不为空
        if (StringUtils.hasText(token)) {
            log.info("检测到 GitHub Token，将用于认证请求。");
            // 将 token 添加到 Authorization 请求头
            requestBuilder.header("Authorization", "token " + token);
        } else {
            log.warn("未配置 GitHub Token。如果仓库是私有的，请求将会失败。");
        }

        try (Response response = okHttpClient.newCall(requestBuilder.build()).execute()) {
            if (!response.isSuccessful()) {
                // 打印更详细的错误信息
                String errorBody = response.body() != null ? response.body().string() : "No response body";
                log.error("请求 GitHub API 失败: Code={}, Message={}, Body={}", response.code(), response.message(), errorBody);
                throw new IOException("请求 GitHub API 失败: " + response.code() + " " + response.message());
            }
            ResponseBody body = response.body();
            if (body == null) {
                throw new IOException("GitHub API 响应体为空");
            }
            return objectMapper.readValue(body.string(), GitHubRelease.class);
        }
    }

    @Override
    public GitHubRelease.Asset findJarAsset(GitHubRelease.Asset[] assets) {
        if (assets == null) return null;
        for (GitHubRelease.Asset asset : assets) {
            if (asset.getName().endsWith(".jar")) {
                return asset;
            }
        }
        return null;
    }

    private void downloadAndReplace(GitHubRelease.Asset jarAsset) throws IOException {
        String downloadUrl = jarAsset.getBrowserDownloadUrl();
        Path currentJarPath = Paths.get(updateProperties.getCurrentJarName()).toAbsolutePath();
        Path tempJarPath = currentJarPath.getParent().resolve("update-temp.jar");

        log.info("从 {} 下载新版本到 {}", downloadUrl, tempJarPath);
        Request request = new Request.Builder().url(downloadUrl).build();
        try (Response response = okHttpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) throw new IOException("下载文件失败: " + response);
            try (InputStream in = response.body().byteStream()) {
                Files.copy(in, tempJarPath, StandardCopyOption.REPLACE_EXISTING);
            }
        }
        log.info("下载完成，准备替换文件...");
        Files.move(tempJarPath, currentJarPath, StandardCopyOption.REPLACE_EXISTING);
        log.info("文件替换成功！");
    }

    @Override
    public void restartApplication() {
        Thread restartThread = new Thread(() -> {
            try {
                // 等待1秒，让当前请求（比如发送"正在重启"消息）有机会完成
                Thread.sleep(1000);
                log.info("应用即将以退出码 {} 强行退出，以触发外部脚本重启...", RESTART_EXIT_CODE);
                // 直接、强制地以指定退出码终止 JVM
                System.exit(RESTART_EXIT_CODE);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.error("重启线程被中断", e);
            }
        });
        // 确保这个线程不会因为主线程退出而中止
        restartThread.setDaemon(false);
        restartThread.start();
    }



    @Override
    public void saveRestartInfo(RestartInfo restartInfo) {
        try {
            File file = new File(RESTART_INFO_FILE);
            objectMapper.writeValue(file, restartInfo);
            log.info("重启信息已保存到 {}", file.getAbsolutePath());
        } catch (IOException e) {
            log.error("保存重启信息文件失败", e);
        }
    }

    private VersionInfo createDefaultVersion(String reason) {
        VersionInfo errorInfo = new VersionInfo();
        errorInfo.name = "未知 (" + reason + ")";
        errorInfo.tagName = "N/A";
        errorInfo.releaseId = -1L;
        errorInfo.assetId = -1L;
        return errorInfo;
    }
}
