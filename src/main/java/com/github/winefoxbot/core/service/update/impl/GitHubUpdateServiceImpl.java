package com.github.winefoxbot.core.service.update.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.winefoxbot.core.config.app.WineFoxBotAppUpdateProperties;
import com.github.winefoxbot.core.model.dto.update.GitHubRelease;
import com.github.winefoxbot.core.model.dto.RestartInfo;
import com.github.winefoxbot.core.model.dto.update.GithubVersionInfo;
import com.github.winefoxbot.core.model.enums.common.MessageType;
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

/**
 * @author FlanChan
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class GitHubUpdateServiceImpl implements GitHubUpdateService {

    private final WineFoxBotAppUpdateProperties updateProperties;
    private final OkHttpClient okHttpClient;
    private final ObjectMapper objectMapper;
    private final ApplicationContext context;

    private GithubVersionInfo currentVersionInfo;
    private static final AtomicBoolean UPDATE_IN_PROGRESS = new AtomicBoolean(false);

    private static final String RESTART_INFO_FILE = "restart-info.json";
    private static final int RESTART_EXIT_CODE = 5;

    @EventListener(ApplicationReadyEvent.class)
    public void initializeGithubVersionInfoOnStartup() {
        log.info("Application is ready. Initializing version info...");
        try {
            ClassPathResource resource = new ClassPathResource("release-info.properties");
            Properties props = PropertiesLoaderUtils.loadProperties(resource);

            this.currentVersionInfo = parsePropertiesToVersionInfo(props);
            log.info("Current version info initialized: {}", this.currentVersionInfo);

        } catch (IOException e) {
            log.error("Failed to read release-info.properties from JAR.", e);
            this.currentVersionInfo = createDefaultVersion();
        }
    }

    private GithubVersionInfo parsePropertiesToVersionInfo(Properties props) {
        GithubVersionInfo info = new GithubVersionInfo();
        info.setReleaseId(parseLong(props.getProperty("github.release.id"), -1));
        info.setTagName(props.getProperty("github.release.tag_name", ""));
        info.setAssetId(parseLong(props.getProperty("github.asset.id"), -1));
        info.setLibAssetId(parseLong(props.getProperty("github.lib.asset.id"), -1));
        info.setLibSha256(props.getProperty("github.lib.sha256", ""));
        info.setResourcesAssetId(parseLong(props.getProperty("github.resources.asset.id"), -1));
        info.setResourcesSha256(props.getProperty("github.resources.sha256", ""));
        return info;
    }

    private long parseLong(String val, long def) {
        try {
            return Long.parseLong(val);
        } catch (NumberFormatException e) {
            return def;
        }
    }

    @Override
    public void performUpdate(Bot bot, AnyMessageEvent event) throws Exception {
        if (!UPDATE_IN_PROGRESS.compareAndSet(false, true)) {
            throw new IllegalStateException("更新已经在进行中，请勿重复操作。");
        }
        try {
            log.info("开始检查应用更新...");
            bot.sendMsg(event, "正在检查更新...", false);

            GitHubRelease latestRelease = fetchLatestRelease();
            GitHubRelease.Asset jarAsset = findJarAsset(latestRelease.getAssets());
            if (jarAsset == null) throw new IllegalStateException("未找到 .jar 文件。");

            String localTag = currentVersionInfo.getTagName();
            String remoteTag = latestRelease.getTagName();

            // 简单 Tag 对比，如果 Tag 一致则认为是最新
            // 注意：如果你想允许同版本下的资源修复，可以去掉这个判断，改用 Hash 强制校验
            if (StringUtils.hasText(localTag) && localTag.equals(remoteTag)) {
                throw new IllegalStateException("当前已是最新版本 (" + localTag + ")。");
            }

            log.info("发现新版本 {}，开始下载主程序...", remoteTag);
            bot.sendMsg(event, "发现新版本 " + remoteTag + "，下载主程序...", false);

            // 1. 下载 Thin JAR
            String tempJarName = "update-temp.jar";
            downloadAsset(jarAsset, tempJarName);

            // 2. 读取新 JAR 中的信息
            Path currentJarDir = Paths.get(updateProperties.getCurrentJarName()).toAbsolutePath().getParent();
            File downloadedJarFile = currentJarDir.resolve(tempJarName).toFile();
            GithubVersionInfo newVersionInfo = readVersionInfoFromJar(downloadedJarFile);

            // ================== Lib 检查 ==================
            GitHubRelease.Asset libAsset = findAssetByName(latestRelease.getAssets(), "lib.zip");
            boolean needDownloadLib = false;
            if (libAsset != null) {
                String localLibHash = currentVersionInfo.getLibSha256();
                String remoteLibHash = newVersionInfo.getLibSha256();
                if (StringUtils.hasText(remoteLibHash) &&
                        (!StringUtils.hasText(localLibHash) || !localLibHash.equals(remoteLibHash))) {
                    needDownloadLib = true;
                }
            }

            GitHubRelease.Asset resAsset = findAssetByName(latestRelease.getAssets(), "resources.zip");
            boolean needDownloadRes = false;
            if (resAsset != null) {
                String localResHash = currentVersionInfo.getResourcesSha256();
                String remoteResHash = newVersionInfo.getResourcesSha256();

                log.info("资源包Hash比对: Local=[{}] vs Remote=[{}]", localResHash, remoteResHash);

                if (StringUtils.hasText(remoteResHash)) {
                    // 如果本地没记录Hash(老版本)，或者Hash不一致，则下载
                    if (!StringUtils.hasText(localResHash) || !localResHash.equals(remoteResHash)) {
                        log.info("检测到静态资源变更，标记为下载。");
                        needDownloadRes = true;
                    }
                }
            }

            // 3. 执行下载
            if (needDownloadLib) {
                bot.sendMsg(event, "检测到依赖库变更，下载 lib.zip...", false);
                downloadAsset(libAsset, "update-lib.zip");
            }

            if (needDownloadRes) {
                bot.sendMsg(event, "检测到静态资源(Web/Template)变更，下载 resources.zip...", false);
                // 下载为 update-resources.zip，供重启脚本解压覆盖
                downloadAsset(resAsset, "update-resources.zip");
            }

            bot.sendMsg(event, "更新包就绪，即将重启...", false);
            restartApplication(event);

        } finally {
            UPDATE_IN_PROGRESS.set(false);
        }
    }

    @Override
    public GithubVersionInfo getCurrentVersionInfo() {
        return this.currentVersionInfo != null ? this.currentVersionInfo : createDefaultVersion();
    }


    @Override
    public GitHubRelease fetchLatestRelease() throws IOException {
        final String apiUrl = updateProperties.getGithubApiUrl().replace("{repo}", updateProperties.getGithubRepo());

        log.info("请求 GitHub API URL: {}", apiUrl);

        Request.Builder requestBuilder = new Request.Builder()
                .url(apiUrl)
                .header("Accept", "application/vnd.github.v3+json")
                .header("User-Agent", "WineFox-Bot-Updater");

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

    private GithubVersionInfo readVersionInfoFromJar(File jarFile) {
        try (JarFile jar = new JarFile(jarFile)) {
            JarEntry entry = jar.getJarEntry("release-info.properties");
            if (entry != null) {
                try (InputStream is = jar.getInputStream(entry)) {
                    Properties props = new Properties();
                    props.load(is);
                    return parsePropertiesToVersionInfo(props);
                }
            }
        } catch (Exception e) {
            log.error("无法从下载的 JAR 中读取版本信息", e);
        }
        return new GithubVersionInfo();
    }

    private GitHubRelease.Asset findAssetByName(GitHubRelease.Asset[] assets, String name) {
        if (assets == null) return null;
        for (GitHubRelease.Asset asset : assets) {
            if (name.equals(asset.getName())) return asset;
        }
        return null;
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

    private GitHubRelease.Asset findLibAsset(GitHubRelease.Asset[] assets) {
        if (assets == null) return null;
        for (GitHubRelease.Asset asset : assets) {
            if ("lib.zip".equals(asset.getName())) {
                return asset;
            }
        }
        return null;
    }



    private GithubVersionInfo createDefaultVersion() {
        GithubVersionInfo errorInfo = new GithubVersionInfo();
        errorInfo.setReleaseId(-1L);
        errorInfo.setAssetId(-1L);
        return errorInfo;
    }
}