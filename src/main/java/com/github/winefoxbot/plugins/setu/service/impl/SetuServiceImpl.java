package com.github.winefoxbot.plugins.setu.service.impl;

import cn.hutool.core.util.URLUtil;
import com.github.winefoxbot.core.config.file.FileStorageProperties;
import com.github.winefoxbot.core.constants.ConfigConstants;
import com.github.winefoxbot.core.exception.bot.NetworkException;
import com.github.winefoxbot.core.exception.bot.ResourceNotFoundException;
import com.github.winefoxbot.core.manager.ConfigManager;
import com.github.winefoxbot.core.model.dto.SendMsgResult;
import com.github.winefoxbot.core.service.file.FileStorageService;
import com.github.winefoxbot.core.utils.FileUploadUtil;
import com.github.winefoxbot.core.utils.FileUtil;
import com.github.winefoxbot.core.utils.PdfUtil;
import com.github.winefoxbot.core.utils.SendMsgUtil;
import com.github.winefoxbot.plugins.setu.config.SetuApiConfig;
import com.github.winefoxbot.plugins.setu.model.dto.SetuApiResponse;
import com.github.winefoxbot.plugins.setu.service.SetuService;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.PathNotFoundException;
import com.mikuac.shiro.common.utils.MsgUtils;
import com.mikuac.shiro.core.Bot;
import com.mikuac.shiro.dto.event.message.AnyMessageEvent;
import com.mikuac.shiro.dto.event.message.GroupMessageEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
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

    private static final int MAX_RETRIES = 3;
    private static final Duration IMAGE_CACHE_DURATION = Duration.ofHours(1);

    @Override
    public void processSetuRequest(Bot bot, AnyMessageEvent event, String tag) {
        processSetuRequestInternal(bot, event, event.getUserId(), event.getGroupId(), tag);
    }

    @Override
    public void processSetuRequest(Bot bot, Long userId, Long groupId, String tag) {
        processSetuRequestInternal(bot, null, userId, groupId, tag);
    }

    /**
     * 内部统一处理方法
     * @param event 可能为 null，如果为 null 则无法使用部分依赖 Event 的高级功能（如抛出给用户的Bot异常、PDF上传等）
     */
    private void processSetuRequestInternal(Bot bot, AnyMessageEvent event, Long userId, Long groupId, String tag) {
        String contentMode = configManager.getOrDefault(ConfigConstants.AdultContent.SETU_CONTENT_MODE, userId, groupId, ConfigConstants.AdultContent.MODE_SFW);
        log.info("开始获取图片任务，标签: '{}', 内容模式: {}", tag, contentMode);

        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                String apiUrl = buildApiUrl(tag, contentMode);
                log.info("第 {}/{} 次尝试, 请求URL: {}", attempt, MAX_RETRIES, apiUrl);
                SetuApiResponse imageUrl = fetchImageUrlFromApi(apiUrl);
                if (imageUrl == null) {
                    log.warn("第 {} 次尝试失败：未能从API获取到有效的图片URL。", attempt);
                    continue;
                }
                log.info("成功获取到图片URL: {}", imageUrl);

                sendImage(bot, event, userId, groupId, imageUrl);
                return;
            } catch (Exception e) {
                log.error("第 {}/{} 次尝试时发生异常", attempt, MAX_RETRIES, e);
            }
        }

        handleException(bot, event, "尝试多次后仍未能获取到图片，请稍后再试~");
    }

    private void handleException(Bot bot, AnyMessageEvent event, String message) {
        // 如果有 Event，抛出 Bot 异常通知用户；如果没有，说明是 AI 调用，抛出 RuntimeException 让 Tool 捕获
        if (event != null) {
            throw new NetworkException(bot, event, message, null);
        } else {
            throw new RuntimeException(message);
        }
    }

    /**
     * 根据内容模式（sfw, r18, mix）构建请求API的URL
     */
    private String buildApiUrl(String tag, String contentMode) {
        HttpUrl.Builder urlBuilder = Objects.requireNonNull(HttpUrl.parse(setuApiConfig.getUrl())).newBuilder();
        SetuApiConfig.Params params = setuApiConfig.getParams();

        if (params.getNum() != null && params.getNum().getKey() != null) {
            urlBuilder.addQueryParameter(params.getNum().getKey(), params.getNum().getValue());
        }
        if (params.getExcludeAI() != null && params.getExcludeAI().getKey() != null) {
            urlBuilder.addQueryParameter(params.getExcludeAI().getKey(), params.getExcludeAI().getValue());
        }
        SetuApiConfig.ContentMode r18Config = params.getMode();
        if (r18Config != null && r18Config.getKey() != null) {
            switch (contentMode) {
                case ConfigConstants.AdultContent.MODE_R18 -> {
                    if (r18Config.getR18ModeValue() != null) {
                        urlBuilder.addQueryParameter(r18Config.getKey(), r18Config.getR18ModeValue());
                    }
                }
                case ConfigConstants.AdultContent.MODE_SFW -> {
                    if (r18Config.getSafeModeValue() != null) {
                        urlBuilder.addQueryParameter(r18Config.getKey(), r18Config.getSafeModeValue());
                    }
                }
                case ConfigConstants.AdultContent.MODE_MIX -> {
                    if (r18Config.getMixModeValue() != null) {
                        urlBuilder.addQueryParameter(r18Config.getKey(), r18Config.getMixModeValue());
                    }
                }
            }
        }
        if (tag != null && !tag.isBlank() && params.getTag() != null && params.getTag().getKey() != null) {
            urlBuilder.addQueryParameter(params.getTag().getKey(), tag);
        }

        return urlBuilder.build().toString();
    }

    /**
     * 从API获取图片URL
     */
    private SetuApiResponse fetchImageUrlFromApi(String url) throws IOException {
        Request request = new Request.Builder().url(url).build();

        if (SetuApiConfig.ResponseType.IMAGE.equals(setuApiConfig.getResponseType())) {
            return new SetuApiResponse(Collections.singletonList(url), true);
        }

        if (SetuApiConfig.ResponseType.JSON.equals(setuApiConfig.getResponseType())) {
            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful() || response.body() == null) {
                    log.error("请求API失败, Code: {}, URL: {}", response.code(), url);
                    return null;
                }
                String jsonBody = response.body().string();
                try {
                    Object result = JsonPath.read(jsonBody, setuApiConfig.getJsonPath());
                    List<String> imageUrls = new ArrayList<>();
                    if (result instanceof List<?> list) {
                        for (Object o : list) {
                            if (o != null) {
                                imageUrls.add(o.toString());
                            }
                        }
                    } else if (result != null) {
                        imageUrls.add(result.toString());
                    }

                    if (imageUrls.isEmpty()) {
                        log.warn("JsonPath '{}' 解析结果为空.", setuApiConfig.getJsonPath());
                        return null;
                    }

                    boolean enabledR18 = false;
                    result = JsonPath.read(jsonBody,setuApiConfig.getResponse().getR18().getJsonPath());
                    if (result instanceof List<?> list) {
                        for (Object o : list) {
                            if (o != null) {
                                if (o.toString().equals(setuApiConfig.getResponse().getR18().getValue())) {
                                    enabledR18 = true;
                                    break;
                                }
                            }
                        }
                    } else if (result != null) {
                        if (result.toString().equals(setuApiConfig.getResponse().getR18().getValue())) {
                            enabledR18 = true;
                        }
                    }
                    return new SetuApiResponse(imageUrls, enabledR18);
                } catch (PathNotFoundException e) {
                    log.error("根据 JsonPath '{}' 未找到图片URL.", setuApiConfig.getJsonPath(), e);
                    return null;
                } catch (Exception e) {
                    log.error("解析API响应JSON时发生错误.", e);
                    return null;
                }
            }
        }
        log.warn("未知的 response-type: '{}'", setuApiConfig.getResponseType());
        return null;
    }

    /**
     * 统一处理图片发送逻辑
     */
    private void sendImage(Bot bot, AnyMessageEvent event, Long userId, Long groupId, SetuApiResponse response) {
        java.util.List<String> urls = response.imgUrls();
        if (urls.isEmpty()) {
            return;
        }

        boolean sendAsPdf = response.enabledR18();

        if (sendAsPdf) {
            java.util.List<Path> imagePaths = new java.util.ArrayList<>();
            for (String url : urls) {
                Path path = getOrDownloadImageFile(url);
                if (path != null) {
                    imagePaths.add(path);
                }
            }
            if (!imagePaths.isEmpty()) {
                sendAsPdfFile(bot, event, imagePaths);
            } else {
                handleException(bot, event, "图片获取失败，无法生成PDF");
            }
        } else {
            for (String url : urls) {
                sendSingleImage(bot, event, userId, groupId, url);
            }
        }
    }

    private Path getOrDownloadImageFile(String url) {
        String cacheKey = generateCacheKeyFromUrl(url);
        Path imagePath = fileStorageService.getFilePathByCacheKey(cacheKey);
        if (imagePath != null) {
            log.info("缓存命中！从本地文件加载图片: {}", imagePath);
            return imagePath;
        }

        log.info("缓存未命中，开始下载图片: {}", url);
        Request imageRequest = new Request.Builder().url(url).build();
        try (Response imageResponse = httpClient.newCall(imageRequest).execute()) {
            if (imageResponse.isSuccessful() && imageResponse.body() != null) {
                byte[] imageBytes = imageResponse.body().bytes();
                fileStorageService.saveFileByCacheKey(cacheKey, imageBytes, IMAGE_CACHE_DURATION);
                return fileStorageService.getFilePathByCacheKey(cacheKey);
            } else {
                log.error("图片下载失败: Code={}, URL={}", imageResponse.code(), url);
            }
        } catch (IOException e) {
            log.error("图片下载异常: URL={}", url, e);
        }
        return null;
    }

    private void sendSingleImage(Bot bot, AnyMessageEvent event, Long userId, Long groupId, String url) {
        String cacheKey = generateCacheKeyFromUrl(url);
        Path imagePath = fileStorageService.getFilePathByCacheKey(cacheKey);

        if (imagePath != null) {
            log.info("缓存命中！发送本地图片: {}", imagePath);
            doSendImage(bot, event, groupId, userId, imagePath.toUri().toString(), false);
            return;
        }

        log.info("缓存未命中，直接下载并发送: {}", url);
        Request imageRequest = new Request.Builder().url(url).build();
        try (Response imageResponse = httpClient.newCall(imageRequest).execute()) {
            if (imageResponse.isSuccessful() && imageResponse.body() != null) {
                byte[] imageBytes = imageResponse.body().bytes();
                doSendImage(bot, event, groupId, userId, imageBytes, true);
                CompletableFuture.runAsync(() -> fileStorageService.saveFileByCacheKey(cacheKey, imageBytes, IMAGE_CACHE_DURATION));
            } else {
                log.error("图片下载失败, URL: {}", url);
            }
        } catch (Exception e) {
            log.error("发送单个图片时出错: {}", url, e);
        }
    }

    /**
     * 底层发送图片方法，兼容有Event和无Event的情况
     */
    private void doSendImage(Bot bot, AnyMessageEvent event, Long groupId, Long userId, Object imageSource, boolean isBytes) {
        String msg = isBytes
                ? MsgUtils.builder().img((byte[]) imageSource).build()
                : MsgUtils.builder().img((String) imageSource).build();

        if (event != null) {
            bot.sendMsg(event, msg, false);
        } else {
            if (groupId != null) {
                SendMsgUtil.sendGroupMsg(bot, groupId, msg, false);
            } else {
                SendMsgUtil.sendPrivateMsg(bot, userId, msg, false);
            }
        }
    }

    private void sendAsPdfFile(Bot bot, AnyMessageEvent event, java.util.List<Path> imagePaths) {
        final Path pdfPath = PdfUtil.wrapImageIntoPdf(imagePaths, fileStorageProperties.getLocal().getBasePath() + File.separator + "setu_tmp");
        if (pdfPath == null) {
            throw new ResourceNotFoundException(bot, event, "PDF文件生成失败", null);
        }
        String fileName = pdfPath.getFileName().toString();
        log.info("准备上传PDF文件: {}", pdfPath);
        Long groupId = event.getGroupId();
        Long userId = event.getUserId();
        CompletableFuture<SendMsgResult> sendFuture = FileUploadUtil.uploadFileAsync(bot, event, pdfPath, fileName);
        sendFuture.whenCompleteAsync((result, throwable) -> {
            if (throwable != null) {
                log.error("PDF upload failed", throwable);
            }
            // 仅在群聊中且配置开启时，才进行撤回操作
            if (event.getGroupId() != null) {
                boolean autoRevoke = configManager.getOrDefault(ConfigConstants.AdultContent.ADULT_AUTO_REVOKE_ENABLED, userId, groupId, true);
                if (result != null && result.isSuccess() && autoRevoke) {
                    deleteGroupFile(bot, event, fileName);
                }
            }
            FileUtil.deleteFileWithRetry(pdfPath.toAbsolutePath().toString());
        });
    }

    private void deleteGroupFile(Bot bot, GroupMessageEvent groupEvent, String fileName) {
        try {
            Long groupId = groupEvent.getGroupId();
            Long userId = groupEvent.getUserId();
            int delay = configManager.getOrDefault(ConfigConstants.AdultContent.ADULT_REVOKE_DELAY_SECONDS, userId, groupId, 30);
            TimeUnit.SECONDS.sleep(delay);
            FileUploadUtil.deleteGroupFile(bot, groupEvent, fileName);
            log.info("群组 {} 已根据配置自动撤回文件: {}", groupEvent.getGroupId(), fileName);
        } catch (InterruptedException e) {
            log.error("撤回群文件时线程被中断", e);
            Thread.currentThread().interrupt();
        }
    }

    private String generateCacheKeyFromUrl(String imageUrl) {
        try {
            URL url = URLUtil.url(imageUrl);
            String path = url.getPath();
            return "setu/" + path.replaceAll("[^a-zA-Z0-9.-]", "_");
        } catch (Exception e) {
            return "setu/" + imageUrl.hashCode();
        }
    }
}
