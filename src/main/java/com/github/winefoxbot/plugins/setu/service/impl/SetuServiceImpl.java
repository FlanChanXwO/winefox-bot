package com.github.winefoxbot.plugins.setu.service.impl;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.URLUtil;
import com.github.winefoxbot.core.config.file.FileStorageProperties;
import com.github.winefoxbot.core.context.BotContext;
import com.github.winefoxbot.core.exception.common.BusinessException;
import com.github.winefoxbot.core.manager.ConfigManager;
import com.github.winefoxbot.core.service.file.FileStorageService;
import com.github.winefoxbot.core.service.shiro.ShiroSafeSendMessageService;
import com.github.winefoxbot.plugins.setu.config.SetuApiConfig;
import com.github.winefoxbot.plugins.setu.model.dto.SetuApiResponse;
import com.github.winefoxbot.plugins.setu.service.SetuService;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.PathNotFoundException;
import com.mikuac.shiro.core.Bot;
import com.mikuac.shiro.dto.event.message.AnyMessageEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Setu 服务实现 (重构版)
 * 集成了 SafeSendMessageService 和 404 重试机制
 *
 * @author FlanChan
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SetuServiceImpl implements SetuService {

    private final OkHttpClient httpClient;
    private final SetuApiConfig setuApiConfig;
    private final FileStorageService fileStorageService;
    private final FileStorageProperties fileStorageProperties;
    private final ConfigManager configManager;
    private final ShiroSafeSendMessageService safeSendMessageService;

    // 缓存时间
    private static final Duration IMAGE_CACHE_DURATION = Duration.ofHours(1);

    // --- 配置 Key 定义 (替代 ConfigConstants) ---
    private static final String KEY_SETU_MODE = "setu.content.mode"; // sfw, r18, mix
    private static final String MODE_SFW = "sfw";
    private static final String MODE_R18 = "r18";
    private static final String MODE_MIX = "mix";

    @Override
    public void processSetuRequest(Bot bot, AnyMessageEvent event, int num, String tag) {
        // 虽然参数传入了 bot 和 event，但主要逻辑依赖 Context (由 Aspect 或 Handler 绑定)
        // 这里的参数仅用于获取 ID
        processInternal(event.getUserId(), event.getGroupId(), num, tag);
    }

    @Override
    public void processSetuRequest(Bot bot, Long userId, Long groupId, int num, String tag) {
        processInternal(userId, groupId, num, tag);
    }

    /**
     * 内部核心处理逻辑
     */
    private void processInternal(Long userId, Long groupId, int num, String tag) {
        // 1. 获取内容模式配置
        String contentMode = configManager.getString(KEY_SETU_MODE, userId, groupId, MODE_SFW);
        log.info("开始获取图片任务: tag='{}', num={}, mode={}", tag, num, contentMode);

        // 2. 构建 API URL 并获取图片列表
        String apiUrl = buildApiUrl(tag, contentMode, num);
        SetuApiResponse apiResponse;
        try {
            apiResponse = fetchImageUrlFromApi(apiUrl);
        } catch (IOException e) {
            throw new RuntimeException("API 请求失败", e);
        }

        if (apiResponse == null || apiResponse.imgUrls().isEmpty()) {
            throw new BusinessException("未能从 API 获取到图片数据");
        }

        List<String> rawUrls = apiResponse.imgUrls();
        boolean isR18 = apiResponse.enabledR18();

        log.info("API 返回图片数: {}, 是否 R18: {}", rawUrls.size(), isR18);

        // 3. 并发下载图片 (包含 404 重试逻辑)
        // 我们必须先下载，一是为了处理 404 重试，二是为了给 SafeService 提供本地文件路径(PDF需要)
        List<Path> downloadedPaths = downloadImagesParallel(rawUrls);

        if (downloadedPaths.isEmpty()) {
            throw new BusinessException("所有图片下载均失败 (包括重试后)");
        }

        // 4. 根据模式调用安全发送服务
        if (isR18) {
            // R18 模式：打包发送 (PDF/ZIP) + 自动撤回
            // 这里的 outputDir 使用临时目录
            String outputDir = fileStorageProperties.getLocal().getBasePath() + File.separator + "setu_tmp";
            String baseName = "Setu_" + System.currentTimeMillis();

            safeSendMessageService.sendSafeFiles(
                    downloadedPaths,
                    outputDir,
                    baseName,
                    result -> log.info("R18 文件发送成功: {}", result.getStatus()),
                    (ex, path) -> log.error("R18 文件发送失败", ex)
            );

        } else {
            // SFW 模式：直接发送图片消息
            // 将本地 Path 转为 URL 字符串供 MsgUtils 使用 (file://...)
            // 这样能确保发送的是我们刚刚辛苦下载并校验过的文件，而不是让 Shiro 再去下载一次 (可能又 404)
            List<String> localUrlList = downloadedPaths.stream()
                    .map(path -> path.toUri().toString())
                    .toList();

            // 构造提示词
            String msg = "找到 " + localUrlList.size() + " 张符合要求的图片~";

            safeSendMessageService.sendMessage(
                    msg,
                    localUrlList,
                    result -> log.info("SFW 图片发送成功: {}", result.getStatus()),
                    ex -> log.error("SFW 图片发送失败", ex)
            );
        }
    }

    /**
     * 并发下载所有图片，并返回成功的本地路径列表
     */
    private List<Path> downloadImagesParallel(List<String> urls) {
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<CompletableFuture<Path>> futures = urls.stream()
                    .map(url -> CompletableFuture.supplyAsync(() -> downloadImageWithRetry(url, false), executor))
                    .toList();

            return futures.stream()
                    .map(CompletableFuture::join) // 等待所有下载完成
                    .filter(Objects::nonNull)     // 过滤失败的
                    .toList();
        }
    }

    /**
     * 下载单个图片，支持 404 重试
     *
     * @param url     图片 URL
     * @param isRetry 是否是重试请求
     * @return 本地文件 Path，失败返回 null
     */
    private Path downloadImageWithRetry(String url, boolean isRetry) {
        String cacheKey = generateCacheKeyFromUrl(url);

        // 1. 查缓存 (仅在非重试时查，如果是重试说明之前肯定没下下来或者想强制刷新)
        if (!isRetry) {
            Path cachedPath = fileStorageService.getFilePathByCacheKey(cacheKey);
            if (cachedPath != null && cachedPath.toFile().exists()) {
                log.debug("图片缓存命中: {}", url);
                return cachedPath;
            }
        }

        // 2. 执行下载
        Request request = new Request.Builder().url(url).build();
        try (Response response = httpClient.newCall(request).execute()) {
            int code = response.code();

            // --- 404 重试逻辑 ---
            if (code == 404 && !isRetry) {
                log.warn("图片下载返回 404，尝试重试一次: {}", url);
                try {
                    // 稍微等待一下，可能是 CDN 同步延迟
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return downloadImageWithRetry(url, true); // 递归调用，标记为 retry
            }
            // ------------------

            if (response.isSuccessful() && response.body() != null) {
                byte[] bytes = response.body().bytes();
                if (bytes.length > 0) {
                    fileStorageService.saveFileByCacheKey(cacheKey, bytes, IMAGE_CACHE_DURATION);
                    return fileStorageService.getFilePathByCacheKey(cacheKey);
                }
            } else {
                log.warn("图片下载失败: Code={}, URL={}, Retry={}", code, url, isRetry);
            }
        } catch (IOException e) {
            log.error("图片下载异常: URL={}, Retry={}", url, isRetry, e);
            // 如果是网络异常（非404响应），也可以考虑重试，这里暂只针对404重试
            if (!isRetry) {
                return downloadImageWithRetry(url, true);
            }
        }
        return null;
    }

    /**
     * 生成 API 请求 URL
     */
    private String buildApiUrl(String tag, String contentMode, int num) {
        HttpUrl.Builder urlBuilder = Objects.requireNonNull(HttpUrl.parse(setuApiConfig.getUrl())).newBuilder();
        SetuApiConfig.Params params = setuApiConfig.getParams();

        // 数量
        if (params.getNum() != null) urlBuilder.addQueryParameter(params.getNum().getKey(), String.valueOf(num));
        // 排除 AI
        if (params.getExcludeAI() != null) urlBuilder.addQueryParameter(params.getExcludeAI().getKey(), params.getExcludeAI().getValue());

        // 模式
        SetuApiConfig.ContentMode modeConfig = params.getMode();
        if (modeConfig != null && modeConfig.getKey() != null) {
            String modeValue = switch (contentMode) {
                case MODE_R18 -> modeConfig.getR18ModeValue();
                case MODE_MIX -> modeConfig.getMixModeValue();
                default -> modeConfig.getSafeModeValue();
            };
            if (modeValue != null) {
                urlBuilder.addQueryParameter(modeConfig.getKey(), modeValue);
            }
        }

        // 标签
        if (tag != null && !tag.isBlank() && params.getTag() != null) {
            urlBuilder.addQueryParameter(params.getTag().getKey(), tag);
        }

        return urlBuilder.build().toString();
    }

    /**
     * 解析 API 响应
     */
    private SetuApiResponse fetchImageUrlFromApi(String url) throws IOException {
        Request request = new Request.Builder().url(url).build();

        // 如果配置直接返回 Image 类型
        if (SetuApiConfig.ResponseType.IMAGE.equals(setuApiConfig.getResponseType())) {
            return new SetuApiResponse(Collections.singletonList(url), true); // 默认为 true 实际上需根据业务判断
        }

        // JSON 解析
        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) {
                log.error("API 请求失败 Code: {}, URL: {}", response.code(), url);
                return null;
            }
            String jsonBody = response.body().string();

            // 1. 提取图片 URLs
            List<String> imageUrls = new ArrayList<>();
            try {
                Object result = JsonPath.read(jsonBody, setuApiConfig.getJsonPath());
                if (result instanceof List<?> list) {
                    list.stream().filter(Objects::nonNull).forEach(o -> imageUrls.add(o.toString()));
                } else if (result != null) {
                    imageUrls.add(result.toString());
                }
            } catch (PathNotFoundException e) {
                log.warn("JsonPath 未找到图片数据: {}", setuApiConfig.getJsonPath());
                return null;
            }

            if (imageUrls.isEmpty()) return null;

            // 2. 检查是否包含 R18 标记 (用于决定是否发 PDF)
            boolean enabledR18 = false;
            try {
                String r18Path = setuApiConfig.getResponse().getR18().getJsonPath();
                String r18Value = setuApiConfig.getResponse().getR18().getValue();
                Object result = JsonPath.read(jsonBody, r18Path);

                // 简化判断逻辑：只要结果中包含目标值即认为 R18
                String resultStr = result.toString();
                if (result instanceof List<?> list) {
                    resultStr = list.toString();
                    if (list.stream().anyMatch(o -> String.valueOf(o).equals(r18Value))) {
                        enabledR18 = true;
                    }
                } else {
                    if (String.valueOf(result).equals(r18Value)) {
                        enabledR18 = true;
                    }
                }
            } catch (Exception e) {
                // 忽略 R18 判断错误，默认为 false
            }

            return new SetuApiResponse(imageUrls, enabledR18);
        }
    }

    private String generateCacheKeyFromUrl(String imageUrl) {
        try {
            String path = URLUtil.url(imageUrl).getPath();
            // 简单清洗文件名
            String cleanName = path.replaceAll("[^a-zA-Z0-9.-]", "_");
            // 避免文件名过长
            if (cleanName.length() > 50) cleanName = cleanName.substring(cleanName.length() - 50);
            return "setu/" + cleanName;
        } catch (Exception e) {
            return "setu/" + imageUrl.hashCode();
        }
    }
}
