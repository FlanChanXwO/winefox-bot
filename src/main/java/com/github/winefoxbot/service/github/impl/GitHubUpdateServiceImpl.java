package com.github.winefoxbot.service.github.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.winefoxbot.config.app.WineFoxBotAppUpdateProperties;
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
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.PropertiesLoaderUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

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

    private VersionInfo currentVersionInfo = null;
    private static final AtomicBoolean updateInProgress = new AtomicBoolean(false);

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
            downloadAndReplace(jarAsset);

            log.info("文件更新完成，即将重启应用...");
            restartApplication();

        } finally {
            updateInProgress.set(false);
        }
    }

    @Override
    public VersionInfo getCurrentVersionInfo() {
        if (this.currentVersionInfo != null) {
            return this.currentVersionInfo;
        }
        VersionInfo info = new VersionInfo();
        try {
            ClassPathResource resource = new ClassPathResource("release-info.properties");
            if (!resource.exists()) {
                log.warn("资源文件 'release-info.properties' 不存在。这在本地开发环境中是正常的。");
            } else {
                Properties props = PropertiesLoaderUtils.loadProperties(resource);
                info.releaseId = Long.parseLong(props.getProperty("github.release.id", "-1").trim());
                info.assetId = Long.parseLong(props.getProperty("github.asset.id", "-1").trim());
            }
        } catch (IOException | NumberFormatException e) {
            log.warn("无法从 'release-info.properties' 文件中读取版本信息，将使用默认值-1。", e);
        }
        this.currentVersionInfo = info;
        return this.currentVersionInfo;
    }

    @Override
    public GitHubRelease fetchLatestRelease() throws IOException {
        final String apiUrl = updateProperties.getGithubApiUrl()
                .replace("{repo}", updateProperties.getGithubRepo())
                .replace("{tag}", updateProperties.getReleaseTag());

        Request.Builder requestBuilder = new Request.Builder()
                .url(apiUrl)
                .header("User-Agent", "Java-App-Updater");

        if (StringUtils.hasText(updateProperties.getGithubToken())) {
            requestBuilder.header("Authorization", "token " + updateProperties.getGithubToken());
        }

        try (Response response = okHttpClient.newCall(requestBuilder.build()).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("请求 GitHub API 失败: " + response.code() + " " + response.message());
            }
            ResponseBody body = response.body();
            if (body == null) throw new IOException("GitHub API 响应体为空");
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
                // 等待1秒，确保日志和消息发送完毕
                Thread.sleep(1000);
                // 关闭当前应用上下文，这将触发应用的优雅停机和重启（如果配置了dev-tools或外部脚本）
                // 如果是简单部署，通常需要外部脚本来拉起
                System.exit(SpringApplication.exit(context, () -> 0));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.error("重启线程被中断", e);
            }
        });
        restartThread.setDaemon(false);
        restartThread.start();
    }
}
