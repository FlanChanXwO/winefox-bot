package com.github.winefoxbot.plugins.setu.service.impl;

import cn.hutool.core.util.URLUtil;
import cn.hutool.http.HttpStatus;
import com.github.winefoxbot.core.config.file.FileStorageProperties;
import com.github.winefoxbot.core.context.BotContext;
import com.github.winefoxbot.core.exception.common.BusinessException;
import com.github.winefoxbot.core.service.file.FileStorageService;
import com.github.winefoxbot.core.utils.ImageObfuscator;
import com.github.winefoxbot.core.model.dto.SendMsgResult;
import com.github.winefoxbot.core.utils.*;
import com.github.winefoxbot.plugins.setu.config.SetuPluginConfig;
import com.github.winefoxbot.plugins.setu.enums.AdultContentMode;
import com.github.winefoxbot.plugins.setu.enums.ContentSendMode;
import com.mikuac.shiro.common.utils.ShiroUtils;
import com.mikuac.shiro.core.Bot;
import com.mikuac.shiro.dto.action.common.ActionData;
import com.mikuac.shiro.dto.action.common.MsgId;
import com.mikuac.shiro.dto.event.message.AnyMessageEvent;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
import com.github.winefoxbot.plugins.setu.model.dto.SetuProviderRequest;
import com.github.winefoxbot.plugins.setu.model.enums.SetuApiType;
import com.github.winefoxbot.plugins.setu.service.SetuImageProvider;
import com.github.winefoxbot.plugins.setu.service.SetuService;
import com.google.common.util.concurrent.Striped;
import com.mikuac.shiro.common.utils.MsgUtils;
import com.mikuac.shiro.dto.event.message.GroupMessageEvent;
import com.mikuac.shiro.dto.event.message.MessageEvent;
import com.mikuac.shiro.dto.event.message.PrivateMessageEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/**
 * Setu 服务实现 (支持策略模式选择 API)
 *
 * @author FlanChan
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SetuServiceImpl implements SetuService {

    private final OkHttpClient httpClient;
    private final Map<String, SetuImageProvider> providerMap;
    private final FileStorageService fileStorageService;
    private final FileStorageProperties fileStorageProperties;
    private final ImageObfuscator imageObfuscator;

    private final Striped<Lock> IMAGE_CACHE_LOCK = Striped.lock(64);

    private static final Duration IMAGE_CACHE_DURATION = Duration.ofHours(1);

    // 最大补货轮次
    private static final int MAX_REPLENISH_ROUNDS = 3;

    private static final long ZIP_THRESHOLD = 100 * 1024 * 1024;

    @Override
    public void handleSetuRequest(int num, List<String> tags) {
        processInternal(num, tags, null, null);
    }

    @Override
    public void handleSetuRequest(int num, List<String> tags, Map<String, Object> extraParams, SetuApiType apiType) {
        processInternal(num, tags, extraParams, apiType);
    }

    /**
     * 内部核心处理逻辑
     */
    private void processInternal(int targetNum, List<String> tags, Map<String, Object> extraParams, SetuApiType apiType) {
        SetuPluginConfig setuConfig = (SetuPluginConfig) BotContext.CURRENT_PLUGIN_CONFIN.get();

        // 1. 获取内容模式配置
        AdultContentMode contentMode = setuConfig.getContentMode(); // 默认为SFW，由Config注入保证
        if (contentMode == null) contentMode = AdultContentMode.SFW;

        // 解析是否需要 R18
        boolean isR18 = determineR18Flag(contentMode);

        // 2. 确定使用哪个 Provider
        SetuImageProvider provider = selectProvider(apiType);
        log.info("开始获取图片任务: Provider={}, tags={}, targetNum={}, mode={} (R18={})",
                provider.getClass().getSimpleName(), tags, targetNum, contentMode, isR18);

        // 3. 构建请求对象
        SetuProviderRequest request = new SetuProviderRequest(tags, targetNum, isR18, extraParams);

        // ================= 阶段一：通过 Provider 获取 URL =================
        List<String> imgUrls = provider.fetchImages(request);

        log.info("从 Provider 获取到 {} 个图片 URL", imgUrls == null ? 0 : imgUrls.size());
        log.info("{}", imgUrls);

        if (imgUrls == null || imgUrls.isEmpty()) {
            throw new BusinessException("未能从 API 获取到图片数据");
        }

        // 并发下载第一批 URL
        List<Path> validPaths = new ArrayList<>(downloadImagesParallel(imgUrls));

        log.info("初步下载完成，目标: {}, 成功: {}, 失败: {}", targetNum, validPaths.size(), targetNum - validPaths.size());

        // ================= 阶段二：智能补货 (应对 404) =================
        int round = 0;
        while (validPaths.size() < targetNum && round < MAX_REPLENISH_ROUNDS) {
            int missingCount = targetNum - validPaths.size();
            log.info("触发补货逻辑 (轮次 {}/{})，缺少 {} 张图片，正在重新请求...", round + 1, MAX_REPLENISH_ROUNDS, missingCount);

            // 并发调用“单张获取”方法来填补空缺，注意要传递 provider 和参数
            List<Path> replenishedPaths = fetchReplacementsParallel(missingCount, provider, tags, isR18, extraParams);

            validPaths.addAll(replenishedPaths);
            round++;
        }

        if (validPaths.isEmpty()) {
            throw new BusinessException("运气不好，一个图片都没拿到...");
        }

        // ================= 阶段三：发送逻辑 =================
        // 注意：这里简单假设只要开启了R18模式或者请求的是R18，就走撤回逻辑
        if (isR18) {
            sendR18Files(validPaths, setuConfig);
        } else {
            sendSfwImages(validPaths, setuConfig);
        }
    }

    /**
     * 策略选择器
     */
    private SetuImageProvider selectProvider(SetuApiType apiType) {
        String beanName;
        // 1. 如果代码明确指定了 API 类型，优先使用
        // 2. 否则使用配置的默认 API 类型
        beanName = Objects.requireNonNullElse(apiType, SetuApiType.LOLICON).getValue();

        SetuImageProvider provider = providerMap.get(beanName);
        if (provider == null) {
            log.warn("未找到名为 '{}' 的 SetuImageProvider Bean，回退到默认 providerMap 第一个", beanName);
            return providerMap.values().stream().findFirst()
                    .orElseThrow(() -> new BusinessException("系统未配置任何 SetuImageProvider 实现"));
        }
        return provider;
    }

    /**
     * 根据模式字符串决定是否请求 R18
     */
    private boolean determineR18Flag(AdultContentMode contentMode) {
        return switch (contentMode) {
            case R18 -> true;
            case MIX -> Math.random() > 0.5;
            default -> false;
        };
    }

    /**
     * 并发补货逻辑
     */
    private List<Path> fetchReplacementsParallel(int count, SetuImageProvider provider, List<String> tag, boolean isR18, Map<String, Object> extraParams) {
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<CompletableFuture<Path>> futures = Stream.generate(() ->
                    CompletableFuture.supplyAsync(() -> fetchAndDownloadSingleImage(provider, tag, isR18, extraParams), executor)
            ).limit(count).toList();

            return futures.stream()
                    .map(CompletableFuture::join)
                    .filter(Objects::nonNull)
                    .toList();
        }
    }

    /**
     * 独立的获取单张图片并下载的方法
     */
    private Path fetchAndDownloadSingleImage(SetuImageProvider provider, List<String> tag, boolean isR18, Map<String, Object> extraParams) {
        try {
            // 1. 请求单张图片的 API 链接 (补货时 num=1)
            SetuProviderRequest request = new SetuProviderRequest(tag, 1, isR18, extraParams);
            List<String> urls = provider.fetchImages(request);

            if (urls == null || urls.isEmpty()) {
                return null;
            }

            String imgUrl = urls.getFirst();
            // 2. 尝试下载
            return downloadImage(imgUrl);

        } catch (Exception e) {
            log.warn("单张补货失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 并发下载 URL 列表，返回成功的 Path
     */
    private List<Path> downloadImagesParallel(List<String> urls) {
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<CompletableFuture<Path>> futures = urls.stream()
                    .map(url -> CompletableFuture.supplyAsync(() -> downloadImage(url), executor))
                    .toList();

            return futures.stream()
                    .map(CompletableFuture::join)
                    .filter(Objects::nonNull)
                    .toList();
        }
    }

    /**
     * 下载单个图片
     */
    private Path downloadImage(String url) {
        String cacheKey = generateCacheKeyFromUrl(url);

        // 1. 查缓存
        Path cachedPath = fileStorageService.getFilePathByCacheKey(cacheKey);
        if (cachedPath != null && cachedPath.toFile().exists()) {
            return cachedPath;
        }

        Lock lock = IMAGE_CACHE_LOCK.get(url);
        lock.lock();
        try {
            // 再次检查缓存，防止并发重复下载
            cachedPath = fileStorageService.getFilePathByCacheKey(cacheKey);
            if (cachedPath != null && cachedPath.toFile().exists()) {
                return cachedPath;
            }
            // 2. 执行下载
            Request request = new Request.Builder().url(url).build();

            try (Response response = httpClient.newCall(request).execute()) {
                int code = response.code();
                if (code == HttpStatus.HTTP_NOT_FOUND) {
                    log.warn("图片链接失效 (404): {}", url);
                    return null;
                }

                ResponseBody body = response.body();
                if (!response.isSuccessful() || body == null) {
                    log.warn("图片下载失败: Code={}, URL={}", code, url);
                    return null;
                }

                // 1. 获取预期的文件大小（可能为 -1，表示未知）
                long expectedLength = body.contentLength();

                try (InputStream is = body.byteStream()) {
                    // 传递 expectedLength 给存储服务，让它在内部做校验
                    // 或者使用原子写入模式（下载到临时文件 -> 成功则重命名）
                    return fileStorageService.saveStreamByCacheKey(
                            cacheKey,
                            is,
                            IMAGE_CACHE_DURATION,
                            expectedLength // <--- 建议修改你的 Service 接口支持传入预期大小
                    );
                } catch (IOException e) {
                    // 2. 关键点：如果流传输中途断开，这里会捕获异常
                    // 你必须确保 fileStorageService 能够感知到失败，并清理掉那个坏文件
                    log.error("流读取中断，清理脏数据: {}", cacheKey, e);
                    fileStorageService.deleteByCacheKey(cacheKey);
                    throw new RuntimeException("图片流传输中断", e);
                }

            } catch (IOException e) {
                throw new RuntimeException("网络请求失败", e);
            }
        } finally {
            lock.unlock();
        }
    }


    private void sendR18Files(List<Path> downloadedPaths, SetuPluginConfig config) {
        String outputDir = fileStorageProperties.getLocal().getBasePath() + File.separator + "setu_tmp";
        String baseName = "Setu_" + System.currentTimeMillis();

        Bot bot = BotContext.CURRENT_BOT.get();
        MessageEvent event = BotContext.CURRENT_MESSAGE_EVENT.get();

        try {
            Path packedFilePath = packFiles(downloadedPaths, outputDir, baseName, true); // force zip/pdf logic for R18
            if (packedFilePath == null) {
                throw new BusinessException("文件打包失败");
            }
            String fileName = packedFilePath.getFileName().toString();

            FileUploadUtil.uploadFileAsync(bot, event, packedFilePath, fileName)
                    .handle((result, throwable) -> {
                        try {
                            if (throwable != null) {
                                log.error("R18 文件上传异常: {}", fileName, throwable);
                                // 上传失败尝试降级混淆发送
                                sendObfuscatedImageMessage(downloadedPaths, "R18文件上传失败，尝试混淆图片发送");
                            } else if (result != null && result.isSuccess()) {
                                log.info("R18 文件发送成功: {}", result.getStatus());
                                // 触发撤回逻辑
                                tryRevokeGroupFile(bot, event, fileName, config);
                            } else {
                                log.warn("R18 文件上传未成功: {}", result);
                                sendObfuscatedImageMessage(downloadedPaths, "R18文件上传未成功，尝试混淆图片发送");
                            }
                        } finally {
                            FileUtil.deleteFileWithRetry(packedFilePath.toAbsolutePath().toString());
                        }
                        return null;
                    });
        } catch (Exception e) {
            log.error("R18 文件发送流程失败", e);
            // 尝试混淆发送
            try {
                sendObfuscatedImageMessage(downloadedPaths, "R18打包发送失败，尝试混淆图片发送");
            } catch (Exception ex) {
                log.error("R18 混淆补发也失败了", ex);
                throw new BusinessException("真正的瑟图被吞了...");
            }
        }
    }


    private void sendSfwImages(List<Path> downloadedPaths, SetuPluginConfig config) {
        ContentSendMode sendMode = (config != null && config.getSendMode() != null)
                ? config.getSendMode()
                : ContentSendMode.IMAGE;

        // 根据配置选择发送方式
        switch (sendMode) {
            case FORWARD -> sendForwardMessage(downloadedPaths);
            case PDF -> sendPdfInternal(downloadedPaths, config);
            default -> sendImageMessage(downloadedPaths);
        }
    }

    private void sendPdfInternal(List<Path> downloadedPaths, SetuPluginConfig config) {
        String outputDir = fileStorageProperties.getLocal().getBasePath() + File.separator + "setu_tmp";
        String baseName = "Setu_SFW_" + System.currentTimeMillis();
        Bot bot = BotContext.CURRENT_BOT.get();
        MessageEvent event = BotContext.CURRENT_MESSAGE_EVENT.get();

        try {
            Path packedFilePath = packFiles(downloadedPaths, outputDir, baseName, false);
            if (packedFilePath == null) throw new BusinessException("文件打包失败");

            String fileName = packedFilePath.getFileName().toString();
            // 上传文件
            FileUploadUtil.uploadFileAsync(bot, event, packedFilePath, fileName)
                    .handle((result, throwable) -> {
                        try {
                            if (throwable != null) {
                                log.error("SFW PDF发送失败", throwable);
                            } else {
                                log.info("SFW PDF发送成功: {}", result != null ? result.getStatus() : "unknown");
                                tryRevokeGroupFile(bot, event, fileName, config);
                            }
                        } finally {
                            FileUtil.deleteFileWithRetry(packedFilePath.toAbsolutePath().toString());
                        }
                        return null;
                    });
        } catch (Exception e) {
            log.error("SFW PDF 发送异常", e);
            throw new BusinessException("PDF 发送失败: " + e.getMessage());
        }
    }

    private void sendForwardMessage(List<Path> downloadedPaths) {
        Bot bot = BotContext.CURRENT_BOT.get();
        AnyMessageEvent event = (AnyMessageEvent) BotContext.CURRENT_MESSAGE_EVENT.get();
        try {
            List<String> msgList = new ArrayList<>();
            // 构造每一个节点的消息
            for (Path path : downloadedPaths) {
                 String fileUrl = path.toUri().toString();
                 String nodeMsg = MsgUtils.builder()
                         .img(fileUrl)
                         .build();
                 msgList.add(nodeMsg);
            }

            // 生成转发消息节点
            List<Map<String, Object>> forwardNodes = ShiroUtils.generateForwardMsg(bot, msgList);
            // 发送
            ActionData<MsgId> result = bot.sendForwardMsg(event, forwardNodes);
            if (result.getRetCode() != 0) {
                 throw new RuntimeException("合并转发发送失败，RetCode=" + result.getRetCode());
            }
            log.info("SFW 合并转发发送成功");

        } catch (Exception e) {
            log.warn("合并转发发送异常，尝试混淆后重发", e);
            try {
                // 混淆并重试转发
                List<Path> obfuscatedPaths = imageObfuscator.wrap(downloadedPaths);
                if (obfuscatedPaths.isEmpty()) throw new RuntimeException("混淆失败");

                List<String> retryMsgList = new ArrayList<>();
                for (Path path : obfuscatedPaths) {
                    retryMsgList.add(MsgUtils.builder().img(path.toUri().toString()).build());
                }
                List<Map<String, Object>> retryNodes = ShiroUtils.generateForwardMsg(bot, retryMsgList);
                ActionData<MsgId> retryResult = bot.sendForwardMsg(event, retryNodes);
                if (retryResult.getRetCode() != 0) {
                    throw new RuntimeException("混淆后合并转发依然失败，RetCode=" + retryResult.getRetCode());
                }
                log.info("SFW 混淆合并转发发送成功");

            } catch (Exception ex) {
                log.error("混淆合并转发终极失败", ex);
                throw new BusinessException("合并转发发送失败: " + e.getMessage());
            }
        }
    }

    private void sendImageMessage(List<Path> downloadedPaths) {
        // 先尝试正常发送
        if (doSendImageMessage(downloadedPaths, false)) {
            return;
        }

        // 失败则混淆重试
        log.warn("SFW 直发失败，尝试混淆后重发");
        try {
            List<Path> obfuscatedPaths = imageObfuscator.wrap(downloadedPaths);
            if (!doSendImageMessage(obfuscatedPaths, true)) {
                 throw new BusinessException("瑟图被严格审核拦截了，混淆也发不出来...");
            }
            log.info("混淆图片补发成功");
        } catch (Exception e) {
            log.error("执行混淆重发流程异常", e);
            throw new BusinessException("发送失败: " + e.getMessage());
        }
    }

    private boolean doSendImageMessage(List<Path> paths, boolean isRetry) {
        if (paths == null || paths.isEmpty()) return false;

        List<String> urlList = paths.stream()
                .map(path -> path.toUri().toString())
                .toList();

        MessageEvent messageEvent = BotContext.CURRENT_MESSAGE_EVENT.get();
        Bot bot = BotContext.CURRENT_BOT.get();

        Integer msgId = switch (messageEvent) {
            case GroupMessageEvent e -> e.getMessageId();
            case PrivateMessageEvent e -> e.getMessageId();
            default -> null;
        };

        MsgUtils builder = MsgUtils.builder();
        if (msgId != null) {
            builder.reply(msgId);
        }

        String msgContent = isRetry
                ? " (混淆重发)" + StringUtils.SPACE + "找到 " + urlList.size() + " 张符合要求的图片~"
                : StringUtils.SPACE + "找到 " + urlList.size() + " 张符合要求的图片~";

        builder.at(messageEvent.getUserId()).text(msgContent);

        for (String url : urlList) {
            builder.img(url);
        }

        try {
            SendMsgResult result = SendMsgUtil.sendMsgByEvent(bot, messageEvent, builder.build(), false);
            if (result.isSuccess()) {
                log.info("SFW 图片发送成功: {}", result.getStatus());
                return true;
            } else {
                log.warn("发送失败: {}", result.getStatus());
                return false;
            }
        } catch (Exception ex) {
            log.warn("SFW 图片发送异常", ex);
            return false;
        }
    }

    private void sendObfuscatedImageMessage(List<Path> originalPaths, String text) {
        try {
            List<Path> obfuscatedPaths = imageObfuscator.wrap(originalPaths);
            if (obfuscatedPaths.isEmpty()) return;

            MessageEvent messageEvent = BotContext.CURRENT_MESSAGE_EVENT.get();
            Bot bot = BotContext.CURRENT_BOT.get();

            Integer msgId = switch (messageEvent) {
                case GroupMessageEvent e -> e.getMessageId();
                case PrivateMessageEvent e -> e.getMessageId();
                default -> null;
            };

            MsgUtils builder = MsgUtils.builder();
            if (msgId != null) builder.reply(msgId);
            builder.text(text);

            for (Path p : obfuscatedPaths) {
                builder.img(p.toUri().toString());
            }

            SendMsgUtil.sendMsgByEvent(bot, messageEvent, builder.build(), false);
        } catch (Exception e) {
            log.error("混淆发送工具方法异常", e);
        }
    }

    private Path packFiles(List<Path> filePaths, String outputDir, String baseName, boolean isR18) throws IOException { // Added 'isR18' param just in case logic differs
        long totalSize = filePaths.stream().mapToLong(p -> p.toFile().length()).sum();
        File dir = new File(outputDir);
        if (!dir.exists()) {
            if (!dir.mkdirs()) {
                throw new IOException("Unable to create directory: " + dir.getAbsolutePath());
            }
        }

        if (totalSize >= ZIP_THRESHOLD) {
            String zipName = baseName + "_" + UUID.randomUUID().toString().substring(0, 8) + ".zip";
            Path zipPath = Paths.get(outputDir, zipName);
            createZip(filePaths, zipPath);
            return zipPath;
        } else {
            return PdfUtil.wrapImageIntoPdf(filePaths, outputDir + File.separator + baseName);
        }
    }

    private void createZip(List<Path> files, Path zipFilePath) throws IOException {
        try (FileOutputStream fos = new FileOutputStream(zipFilePath.toFile());
             ZipArchiveOutputStream zaos = new ZipArchiveOutputStream(fos)) {
            for (Path path : files) {
                File file = path.toFile();
                if (file.exists() && file.isFile()) {
                    ZipArchiveEntry entry = new ZipArchiveEntry(file.getName());
                    zaos.putArchiveEntry(entry);
                    try (FileInputStream fis = new FileInputStream(file)) {
                        fis.transferTo(zaos);
                    }
                    zaos.closeArchiveEntry();
                }
            }
        }
    }

    private void tryRevokeGroupFile(Bot bot, MessageEvent event, String fileName, SetuPluginConfig config) {
        Long groupId = null;
        if (event instanceof GroupMessageEvent ge) {
            groupId = ge.getGroupId();
        }

        if (groupId == null) {
            return;
        }

        boolean shouldRevoke = config.isRevokeEnabled();

        if (!shouldRevoke) {
            return;
        }

        int delay = config.getRevokeDelay();

        log.info("将在 {} 秒后撤回群组 {} 的文件: {}", delay, groupId, fileName);

        Long finalGroupId = groupId;
        CompletableFuture.delayedExecutor(delay, TimeUnit.SECONDS).execute(() -> {
            try {
                GroupMessageEvent ge = (GroupMessageEvent) event;
                FileUploadUtil.deleteGroupFile(bot, ge, fileName);
                log.info("群组 {} 已撤回文件: {}", finalGroupId, fileName);
            } catch (Exception e) {
                log.error("自动撤回群文件失败: {}", fileName, e);
            }
        });
    }

    private String generateCacheKeyFromUrl(String imageUrl) {
        try {
            String path = URLUtil.url(imageUrl).getPath();
            String cleanName = path.replaceAll("[^a-zA-Z0-9.-]", "_");
            if (cleanName.length() > 50) cleanName = cleanName.substring(cleanName.length() - 50);
            return "setu/" + cleanName;
        } catch (Exception e) {
            return "setu/" + imageUrl.hashCode();
        }
    }
}
