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
import java.util.concurrent.atomic.AtomicBoolean;

@Service
@Slf4j
@RequiredArgsConstructor
public class GitHubUpdateServiceImpl implements GitHubUpdateService {

    private final WineFoxBotAppUpdateProperties updateProperties;
    private final OkHttpClient okHttpClient;
    private final ObjectMapper objectMapper;
    private final ApplicationContext context;

    private VersionInfo currentVersionInfo;
    private static final AtomicBoolean updateInProgress = new AtomicBoolean(false);

    private static final String RESTART_INFO_FILE = "restart-info.json";
    private static final int RESTART_EXIT_CODE = 5;

    @EventListener(ApplicationReadyEvent.class)
    public void initializeVersionInfoOnStartup() {
        log.info("Application is ready. Initializing version info from embedded properties...");
        try {
            ClassPathResource resource = new ClassPathResource("release-info.properties");
            Properties props = PropertiesLoaderUtils.loadProperties(resource);
            long releaseId = Long.parseLong(props.getProperty("github.release.id", "-1"));
            long assetId = Long.parseLong(props.getProperty("github.asset.id", "-1"));

            if (releaseId == -1 || assetId == -1) {
                log.warn("Could not find valid release/asset ID in release-info.properties. Update checks may fail.");
            }

            this.currentVersionInfo = new VersionInfo();
            this.currentVersionInfo.releaseId = releaseId;
            this.currentVersionInfo.assetId = assetId;

            log.info("Successfully initialized current version info: {}", this.currentVersionInfo);

        } catch (IOException | NumberFormatException e) {
            log.error("Failed to read or parse release-info.properties from JAR. Using default error values.", e);
            this.currentVersionInfo = createDefaultVersion("读取内置版本信息失败");
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

            VersionInfo latestVersion = new VersionInfo();
            latestVersion.releaseId = latestRelease.getId();
            latestVersion.assetId = jarAsset.getId();
            log.info("检测到最新版本 - Release ID: {}, Asset ID: {}", latestVersion.releaseId, latestVersion.assetId);

            VersionInfo currentVersion = getCurrentVersionInfo();
            log.info("当前运行版本 - Release ID: {}, Asset ID: {}", currentVersion.releaseId, currentVersion.assetId);

            if (latestVersion.releaseId == currentVersion.releaseId && latestVersion.assetId == currentVersion.assetId) {
                throw new IllegalStateException("当前已是最新版本，无需更新。");
            }
            if (latestVersion.releaseId < currentVersion.releaseId) {
                throw new IllegalStateException("检测到的线上版本比当前版本更旧，跳过更新。");
            }

            log.info("发现新版本，开始下载并替换...");
            // 调用已修复的下载方法
            downloadAndReplace(jarAsset);

            log.info("文件更新完成，即将重启应用...");
            restartApplication();

        } finally {
            updateInProgress.set(false);
        }
    }

    @Override
    public VersionInfo getCurrentVersionInfo() {
        if (this.currentVersionInfo == null) {
            log.warn("Current version info has not been initialized yet. Returning a default error version.");
            return createDefaultVersion("未初始化");
        }
        return this.currentVersionInfo;
    }

    @Override
    public GitHubRelease fetchLatestRelease() throws IOException {
        final String apiUrl = updateProperties.getGithubApiUrl()
                .replace("{repo}", updateProperties.getGithubRepo())
                .replace("{tag}", updateProperties.getReleaseTag());

        Request.Builder requestBuilder = new Request.Builder()
                .url(apiUrl)
                .header("Accept", "application/vnd.github.v3+json")
                .header("User-Agent", "WineFox-Bot-Updater");

        String token = updateProperties.getGithubToken();
        if (StringUtils.hasText(token)) {
            log.info("检测到 GitHub Token，将用于认证请求。");
            requestBuilder.header("Authorization", "token " + token);
        } else {
            log.warn("未配置 GitHub Token。如果仓库是私有的，请求将会失败。");
        }

        try (Response response = okHttpClient.newCall(requestBuilder.build()).execute()) {
            if (!response.isSuccessful()) {
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

    /**
     * 【核心修复】修改下载逻辑以支持私有仓库
     */
    private void downloadAndReplace(GitHubRelease.Asset jarAsset) throws IOException {
        // 对于私有仓库，不能使用 browser_download_url，必须使用 API URL (asset.getUrl())
        String downloadUrl = jarAsset.getUrl();
        Path currentJarPath = Paths.get(updateProperties.getCurrentJarName()).toAbsolutePath();
        Path tempJarPath = currentJarPath.getParent().resolve("update-temp.jar");

        log.info("从 API URL {} 下载新版本到 {}", downloadUrl, tempJarPath);

        Request.Builder requestBuilder = new Request.Builder()
                .url(downloadUrl)
                // 关键！GitHub 要求下载 Asset 时 Accept 头必须是这个
                .header("Accept", "application/octet-stream")
                .header("User-Agent", "WineFox-Bot-Updater");

        // 关键！从配置中获取 Token 并添加到请求头
        String token = updateProperties.getGithubToken();
        if (StringUtils.hasText(token)) {
            requestBuilder.header("Authorization", "token " + token);
        } else {
            // 如果到了这一步还没有 token，私有仓库的下载必然失败，直接报错
            throw new IOException("无法下载私有仓库文件：未配置 GitHub Token。");
        }

        try (Response response = okHttpClient.newCall(requestBuilder.build()).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("下载文件失败: " + response);
            }
            try (InputStream in = response.body().byteStream()) {
                Files.copy(in, tempJarPath, StandardCopyOption.REPLACE_EXISTING);
            }
        }
        log.info("下载完成，准备替换文件...");
        Files.move(tempJarPath, currentJarPath, StandardCopyOption.REPLACE_EXISTING);
        log.info("文件替换成功！");
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

    @Override
    public void restartApplication() {
        Thread restartThread = new Thread(() -> {
            try {
                Thread.sleep(1000);
                log.info("应用即将以退出码 {} 强行退出，以触发外部脚本重启...", RESTART_EXIT_CODE);
                System.exit(RESTART_EXIT_CODE);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.error("重启线程被中断", e);
            }
        });
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