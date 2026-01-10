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
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

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
            String tagName = props.getProperty("github.release.tag_name", "");
            String libSha256 = props.getProperty("github.lib.sha256", "");
            if (releaseId == -1 && !StringUtils.hasText(tagName)) {
                log.warn("Could not find valid release info in properties.");
            }

            this.currentVersionInfo = new VersionInfo();
            this.currentVersionInfo.releaseId = releaseId;
            this.currentVersionInfo.tagName = tagName;
            this.currentVersionInfo.assetId = assetId;
            this.currentVersionInfo.libAssetId = libAssetId;
            this.currentVersionInfo.libSha256 = libSha256;
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

            // 1. 获取线上最新版本信息
            GitHubRelease latestRelease = fetchLatestRelease();
            GitHubRelease.Asset jarAsset = findJarAsset(latestRelease.getAssets());
            if (jarAsset == null) {
                throw new IllegalStateException("未找到 .jar 文件。");
            }
            String localTag = currentVersionInfo.tagName;
            String remoteTag = latestRelease.getTagName();
            log.info("版本比对: Local=[{}] vs Remote=[{}]", localTag, remoteTag);

            if (StringUtils.hasText(localTag) && localTag.equals(remoteTag)) {
                throw new IllegalStateException("当前已是最新版本 (" + localTag + ")，无需更新。");
            }

            log.info("发现新版本 {}，开始处理...", latestRelease.getTagName());
            bot.sendMsg(event, "发现新版本 " + latestRelease.getTagName() + "，正在下载主程序...", false);

            // 2. 先下载 Thin JAR (这个文件很小，先下它)
            String tempJarName = "update-temp.jar";
            downloadAsset(jarAsset, tempJarName);

            // 获取临时文件路径
            Path currentJarDir = Paths.get(updateProperties.getCurrentJarName()).toAbsolutePath().getParent();
            File downloadedJarFile = currentJarDir.resolve(tempJarName).toFile();

            // 3. 【关键步骤】读取新下载 JAR 包内部的属性
            VersionInfo newVersionInfo = readVersionInfoFromJar(downloadedJarFile);
            log.info("新版本信息详情: {}", newVersionInfo);

            // 4. 智能检测是否需要下载 lib.zip
            // 从 API 返回的 assets 列表中寻找 lib.zip
            GitHubRelease.Asset libAsset = findLibAsset(latestRelease.getAssets());
            boolean needDownloadLib = false;

            // 只有当远程 Release 确实包含 lib.zip 时才进行检测
            if (libAsset != null) {
                String localHash = currentVersionInfo.libSha256; // 当前内存中的哈希
                String remoteHash = newVersionInfo.libSha256;    // 新下载Jar包里记录的哈希

                log.info("依赖库哈希比对: Local=[{}] vs Remote=[{}]", localHash, remoteHash);

                // 核心判断逻辑：
                // 1. 如果远程有哈希值 (说明新版确实生成了 lib 信息)
                if (StringUtils.hasText(remoteHash)) {
                    // 2. 如果本地哈希为空 (说明是从旧版本升级上来)，或者哈希不一致 -> 下载
                    if (!StringUtils.hasText(localHash) || !localHash.equals(remoteHash)) {
                        log.info("检测到依赖库变更 (或本地缺失哈希)，准备下载 lib.zip。");
                        needDownloadLib = true;
                    } else {
                        log.info("依赖库哈希一致，跳过 lib.zip 下载。");
                    }
                } else {
                    // 远程有文件但没哈希？(罕见情况) 为了保险起见，建议下载，或者忽略
                    log.warn("远程 lib.zip 存在但未读取到哈希值，跳过下载以防错误。");
                }
            }

            // 5. 如果需要，下载 lib.zip
            if (needDownloadLib) {
                bot.sendMsg(event, "检测到依赖库变更，正在下载 lib.zip (可能耗时较长)...", false);
                // 下载并保存为 update-lib.zip，供重启脚本处理
                downloadAsset(libAsset, "update-lib.zip");
            }


            bot.sendMsg(event, "更新包准备就绪，应用即将重启...", false);
            restartApplication(event);

        } finally {
            updateInProgress.set(false);
        }
    }

    private VersionInfo readVersionInfoFromJar(File jarFile) {
        VersionInfo info = new VersionInfo();
        try (JarFile jar = new JarFile(jarFile)) {
            JarEntry entry = jar.getJarEntry("release-info.properties");
            if (entry != null) {
                try (InputStream is = jar.getInputStream(entry)) {
                    Properties props = new Properties();
                    props.load(is);
                    info.releaseId = Long.parseLong(props.getProperty("github.release.id", "-1"));
                    info.tagName = props.getProperty("github.release.tag_name", "");
                    info.assetId = Long.parseLong(props.getProperty("github.asset.id", "-1"));
                    info.libAssetId = Long.parseLong(props.getProperty("github.lib.asset.id", "-1"));
                    info.libSha256 = props.getProperty("github.lib.sha256", "");
                }
            }
        } catch (Exception e) {
            log.error("无法从下载的 JAR 中读取版本信息", e);
        }
        return info;
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
        final String apiUrl = updateProperties.getGithubApiUrl().replace("{repo}", updateProperties.getGithubRepo());

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