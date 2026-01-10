package com.github.winefoxbot.core.service.update.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.winefoxbot.core.config.app.WineFoxBotAppUpdateProperties;
import com.github.winefoxbot.core.model.dto.GitHubRelease;
import com.github.winefoxbot.core.model.dto.RestartInfo;
import com.github.winefoxbot.core.model.enums.MessageType;
import com.github.winefoxbot.core.service.update.GitHubUpdateService;
import com.mikuac.shiro.core.Bot;
import com.mikuac.shiro.dto.event.message.AnyMessageEvent;
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
            long libAssetId = Long.parseLong(props.getProperty("github.lib.asset.id", "-1"));

            if (releaseId == -1 || assetId == -1) {
                log.warn("Could not find valid release/asset ID in release-info.properties. Update checks may fail.");
            }

            this.currentVersionInfo = new VersionInfo();
            this.currentVersionInfo.releaseId = releaseId;
            this.currentVersionInfo.assetId = assetId;
            this.currentVersionInfo.libAssetId = libAssetId;

            log.info("Successfully initialized current version info: {}", this.currentVersionInfo);

        } catch (IOException | NumberFormatException e) {
            log.error("Failed to read or parse release-info.properties from JAR. Using default error values.", e);
            this.currentVersionInfo = createDefaultVersion();
        }
    }

    @Override
    public void performUpdate(Bot bot, AnyMessageEvent event) throws Exception {
        if (!updateInProgress.compareAndSet(false, true)) {
            throw new IllegalStateException("更新已经在进行中，请勿重复操作。");
        }
        try {
            log.info("开始检查应用更新...");
            bot.sendMsg(event, "正在检查更新，请稍候...", false);
            GitHubRelease latestRelease = fetchLatestRelease();
            GitHubRelease.Asset jarAsset = findJarAsset(latestRelease.getAssets());
            if (jarAsset == null) {
                throw new IllegalStateException("在 Release '" + latestRelease.getTagName() + "' 中未找到 .jar 文件。");
            }

            VersionInfo latestVersion = new VersionInfo();
            latestVersion.releaseId = latestRelease.getId();
            latestVersion.assetId = jarAsset.getId();

            GitHubRelease.Asset libAsset = findLibAsset(latestRelease.getAssets());
            if (libAsset != null) {
                latestVersion.libAssetId = libAsset.getId();
            }

            log.info("检测到最新版本: {}", latestVersion);
            VersionInfo currentVersion = getCurrentVersionInfo();
            log.info("当前运行版本: {}", currentVersion);

            if (latestVersion.releaseId == currentVersion.releaseId) {
                log.info("Release ID 相同，视为已是最新版本。");
                throw new IllegalStateException("当前已是最新版本，无需更新。");
            }

            if (latestVersion.releaseId < currentVersion.releaseId) {
                throw new IllegalStateException("检测到的线上版本比当前版本更旧，跳过更新。");
            }

            log.info("发现新版本，开始下载并替换...");
            bot.sendMsg(event, "发现新版本 " + latestRelease.getTagName() + "，正在下载更新包...", false);

            // Download Thin JAR
            downloadAsset(jarAsset, "update-temp");

            // Download Libs if needed
            // Logic: If remote has lib.zip AND (local lib ID is different OR local lib ID is missing)
            if (libAsset != null) {
                if (currentVersion.libAssetId != libAsset.getId()) {
                     log.info("依赖库版本变更 (Local: {}, Remote: {})，正在下载依赖库...", currentVersion.libAssetId, libAsset.getId());
                     bot.sendMsg(event, "正在下载依赖库更新 (lib.zip)...", false);
                     downloadAsset(libAsset, "update-lib.zip");
                } else {
                     log.info("依赖库版本一致，跳过下载 lib.zip");
                }
            } else {
                log.warn("线上 Release 未包含 lib.zip，如果是 Thin JAR 模式可能导致无法运行。");
            }

            bot.sendMsg(event, "更新包下载完成，应用即将重启以完成更新...", false);
            log.info("文件更新完成，即将重启应用...");

            restartApplication(event);

        } finally {
            updateInProgress.set(false);
        }
    }

    @Override
    public VersionInfo getCurrentVersionInfo() {
        if (this.currentVersionInfo == null) {
            log.warn("Current version info has not been initialized yet. Returning a default error version.");
            return createDefaultVersion();
        }
        return this.currentVersionInfo;
    }

    @Override
    public GitHubRelease fetchLatestRelease() throws IOException {
        final String apiUrl = updateProperties.getGithubApiUrl()
                .replace("{repo}", updateProperties.getGithubRepo())
                .replace("{tag}", updateProperties.getReleaseTag());

        log.info("请求 GitHub API URL: {}", apiUrl);

        Request.Builder requestBuilder = new Request.Builder()
                .url(apiUrl)
                .header("Accept", "application/vnd.github.v3+json")
                .header("User-Agent", "WineFox-Bot-Updater");

        String token = updateProperties.getGithubToken();
        if (StringUtils.hasText(token)) {
            log.info("检测到 GitHub Token，将用于认证请求。");
            log.info("DIAGNOSTIC - Using GitHub Token: [{}]", token);
            log.info("检测到 GitHub Token，将用于认证请求。");
            requestBuilder.header("Authorization", "token " + token);
        } else {
            log.warn("未配置 GitHub Token。如果仓库是私有的，请求将会失败。");
        }

        try (Response response = okHttpClient.newCall(requestBuilder.build()).execute()) {
            ResponseBody body = response.body();
            if (body == null) {
                // 即使请求失败，body也可能为null，先处理这种情况
                throw new IOException("GitHub API 响应体为空。响应码: " + response.code());
            }

            // 只读取一次响应体，并存入字符串变量
            final String responseBodyString = body.string();

            if (!response.isSuccessful()) {
                log.error("请求 GitHub API 失败: Code={}, Message={}, Body={}", response.code(), response.message(), responseBodyString);
                throw new IOException("请求 GitHub API 失败: " + response.code() + " " + response.message());
            }

            // 请求成功，使用已读取的字符串进行反序列化
            return objectMapper.readValue(responseBodyString, GitHubRelease.class);
        }
    }


    private void downloadAsset(GitHubRelease.Asset asset, String targetFileName) throws IOException {
        String downloadUrl = asset.getUrl();
        // 获取当前 JAR 所在的目录
        Path currentJarDir = Paths.get(updateProperties.getCurrentJarName()).toAbsolutePath().getParent();
        // 最终的临时文件路径
        Path targetPath = currentJarDir.resolve(targetFileName);


        log.info("从 API URL {} 下载文件到 {}", downloadUrl, targetPath);

        Request.Builder requestBuilder = new Request.Builder()
                .url(downloadUrl)
                .header("Accept", "application/octet-stream")
                .header("User-Agent", "WineFox-Bot-Updater");

        String token = updateProperties.getGithubToken();
        if (StringUtils.hasText(token)) {
            requestBuilder.header("Authorization", "token " + token);
        } else {
            throw new IOException("无法下载私有仓库文件：未配置 GitHub Token。");
        }

        try (Response response = okHttpClient.newCall(requestBuilder.build()).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("下载文件失败: " + response);
            }
            ResponseBody body = response.body();
            if (body == null) {
                throw new IOException("下载失败: 响应体为空");
            }
            try (InputStream in = body.byteStream()) {
                // 将下载的文件直接保存为目标文件，并覆盖已有的
                Files.copy(in, targetPath, StandardCopyOption.REPLACE_EXISTING);
            }
        }
        log.info("文件已成功下载到 {}", targetPath);
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

    private GitHubRelease.Asset findLibAsset(GitHubRelease.Asset[] assets) {
        if (assets == null) return null;
        for (GitHubRelease.Asset asset : assets) {
            if ("lib.zip".equals(asset.getName())) {
                return asset;
            }
        }
        return null;
    }

    @Override
    public void restartApplication(AnyMessageEvent event) {
        MessageType messageType = MessageType.fromValue(event.getMessageType());
        Long targetId = switch (messageType) {
            case GROUP -> event.getGroupId();
            case PRIVATE -> event.getUserId();
        };
        String successMsgTemplate = String.format("[CQ:at,qq=%d] 应用重启成功！\n耗时: {duration}\n当前版本: {version}", event.getUserId());
        long startTime = System.currentTimeMillis();
        RestartInfo restartInfo = new RestartInfo(messageType, targetId, successMsgTemplate, startTime);
        saveRestartInfo(restartInfo);
        Thread restartThread = new Thread(() -> {
            try {
                // 等待1秒，让当前请求（比如发送"正在重启"消息）有机会完成
                Thread.sleep(1000);
                // ==================【【【 最重要的诊断日志 】】】==================
                log.info(">>>>>>>>> [DIAGNOSTIC] 使用 SpringApplication.exit() 重启模式 <<<<<<<<<");
                // ====================================================================
                log.info("应用即将以退出码 {} 关闭，以触发外部脚本重启...", RESTART_EXIT_CODE);
                // 这会优雅地关闭 Spring 上下文，并返回退出码，而不会终止父 bat 脚本。
                int exitCode = SpringApplication.exit(context, () -> RESTART_EXIT_CODE);
                System.exit(exitCode);

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

    private VersionInfo createDefaultVersion() {
        VersionInfo errorInfo = new VersionInfo();
        errorInfo.releaseId = -1L;
        errorInfo.assetId = -1L;
        return errorInfo;
    }
}