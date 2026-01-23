package com.github.winefoxbot.plugins.setu.service.impl;

import cn.hutool.core.util.URLUtil;
import cn.hutool.http.HttpStatus;
import com.github.winefoxbot.core.config.file.FileStorageProperties;
import com.github.winefoxbot.core.context.BotContext;
import com.github.winefoxbot.core.exception.common.BusinessException;
import com.github.winefoxbot.core.manager.ConfigManager;
import com.github.winefoxbot.core.service.file.FileStorageService;
import com.github.winefoxbot.core.service.shiro.ShiroSafeSendMessageService;
import com.github.winefoxbot.core.utils.ImageObfuscator;
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
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
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
    private final ConfigManager configManager;
    private final ShiroSafeSendMessageService safeSendMessageService;
    private final ImageObfuscator imageObfuscator;

    private final Striped<Lock> IMAGE_CACHE_LOCK = Striped.lock(64);

    private static final Duration IMAGE_CACHE_DURATION = Duration.ofHours(1);

    private static final String KEY_SETU_MODE = "setu.content.mode";

    private static final String MODE_SFW = "sfw";
    private static final String MODE_R18 = "r18";
    private static final String MODE_MIX = "mix";

    // 最大补货轮次
    private static final int MAX_REPLENISH_ROUNDS = 3;

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
        MessageEvent messageEvent = BotContext.CURRENT_MESSAGE_EVENT.get();
        Long userId = messageEvent.getUserId();
        Long groupId = null;
        if (messageEvent instanceof GroupMessageEvent e) {
            groupId = e.getGroupId();
        }

        // 1. 获取内容模式配置
        String contentMode = configManager.getString(KEY_SETU_MODE, userId, groupId, MODE_SFW);
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
            sendR18Files(validPaths);
        } else {
            sendSfwImages(validPaths);
        }
    }

    /**
     * 策略选择器
     */
    private SetuImageProvider selectProvider(SetuApiType apiType) {
        String beanName;
        if (apiType != null) {
            // 1. 如果代码明确指定了 API 类型，优先使用
            beanName = apiType.getValue();
        } else {
            // 2. 否则使用配置的默认 API 类型
            beanName = SetuApiType.LOLICON.getValue();
        }

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
    private boolean determineR18Flag(String contentMode) {
        return switch (contentMode) {
            case MODE_R18 -> true;
            case MODE_MIX -> Math.random() > 0.5;
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


    private void sendR18Files(List<Path> downloadedPaths) {
        String outputDir = fileStorageProperties.getLocal().getBasePath() + File.separator + "setu_tmp";
        String baseName = "Setu_" + System.currentTimeMillis();

        safeSendMessageService.sendSafeFiles(
                downloadedPaths,
                outputDir,
                baseName,
                result -> log.info("R18 文件发送成功: {}", result.getStatus()),
                (ex, path) -> {
                    log.error("R18 文件发送失败", ex);
                    if (ex instanceof RuntimeException re) throw re;
                    throw new BusinessException("真正的瑟图被吞了...");
                }
        );
    }


    private void sendSfwImages(List<Path> downloadedPaths) {
        List<String> localUrlList = downloadedPaths.stream()
                .map(path -> path.toUri().toString())
                .toList();

        // 构建回复消息
        MessageEvent messageEvent = BotContext.CURRENT_MESSAGE_EVENT.get();
        Integer msgId = switch (messageEvent) {
            case GroupMessageEvent e -> e.getMessageId();
            case PrivateMessageEvent e -> e.getMessageId();
            default -> null;
        };
        MsgUtils builder = MsgUtils.builder();
        if (msgId != null) {
            builder.reply(msgId);
        }
        String msg = builder.at(messageEvent.getUserId())
                .text(StringUtils.SPACE + "找到 " + localUrlList.size() + " 张符合要求的图片~")
                .build();

        safeSendMessageService.sendMessage(
                msg,
                localUrlList,
                result -> log.info("SFW 图片发送成功: {}", result.getStatus()),
                ex -> {
                    log.warn("SFW 直发失败，尝试混淆后重发: {}", ex.getMessage());

                    // === 新增：混淆重试逻辑 ===
                    try {
                        // 1. 使用工具类包装图片
                        List<Path> obfuscatedPaths = imageObfuscator.wrap(downloadedPaths);

                        if (obfuscatedPaths.isEmpty()) {
                            throw new BusinessException("图片混淆失败，无法重试");
                        }

                        List<String> newUrlList = obfuscatedPaths.stream()
                                .map(path -> path.toUri().toString())
                                .toList();

                        // 2. 尝试再次发送
                        safeSendMessageService.sendMessage(
                                msg ,
                                newUrlList,
                                res -> log.info("混淆图片补发成功"),
                                retryEx -> {
                                    log.error("混淆图片补发依然失败", retryEx);
                                    // 只有这里才抛出最终异常
                                    throw new BusinessException("瑟图被严格审核拦截了，发不出来...");
                                }
                        );

                    } catch (Exception e) {
                        log.error("执行混淆重发流程异常", e);
                        if (e instanceof RuntimeException re) throw re;
                    }
                }
        );
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
