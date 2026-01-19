package com.github.winefoxbot.plugins.setu.service.impl;

import cn.hutool.core.util.URLUtil;
import com.github.winefoxbot.core.config.file.FileStorageProperties;
import com.github.winefoxbot.core.constants.ConfigConstants;
import com.github.winefoxbot.core.exception.bot.BotException;
import com.github.winefoxbot.core.exception.common.BusinessException;
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
import com.mikuac.shiro.common.utils.ShiroUtils;
import com.mikuac.shiro.core.Bot;
import com.mikuac.shiro.dto.action.common.ActionData;
import com.mikuac.shiro.dto.action.common.MsgId;
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
import java.net.URI;
import java.net.URL;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;

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

    private static final Duration IMAGE_CACHE_DURATION = Duration.ofHours(1);


    /**
     * 图片源的封装类，用于统一处理字节数组和URI。
     */
    private record ImageSource(
            byte[] bytes,
            URI uri) {
        ImageSource(URI uri) {
            this(null, uri);
        }
        ImageSource(byte[] bytes) {
            this(bytes, null);
        }
        boolean isBytes() {
            return bytes != null;
        }
    }

    @Override
    public void processSetuRequest(Bot bot, AnyMessageEvent event, int num, String tag) {
        processSetuRequestInternal(bot, event, event.getUserId(), event.getGroupId(), num, tag);
    }

    @Override
    public void processSetuRequest(Bot bot, Long userId, Long groupId, int num, String tag) {
        processSetuRequestInternal(bot, null, userId, groupId, num, tag);
    }

    /**
     * 内部统一处理方法
     *
     * @param event 可能为 null，如果为 null 则无法使用部分依赖 Event 的高级功能（如抛出给用户的Bot异常、PDF上传等）
     * @param num
     */
    private void processSetuRequestInternal(Bot bot, AnyMessageEvent event, Long userId, Long groupId, int num, String tag) {
        String contentMode = configManager.getOrDefault(ConfigConstants.AdultContent.SETU_CONTENT_MODE, userId, groupId, ConfigConstants.AdultContent.MODE_SFW);
        log.info("开始获取图片任务，标签: '{}', 内容模式: {}", tag, contentMode);
        String apiUrl = buildApiUrl(tag, contentMode, num);
        SetuApiResponse imageUrl;
        try {
            imageUrl = fetchImageUrlFromApi(apiUrl);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        if (imageUrl == null) {
            throw new BusinessException("未能从API获取到有效的图片URL");
        }
        log.info("成功获取到图片URL: {}", imageUrl);
        sendImage(bot, event, userId, groupId, imageUrl);
    }

    /**
     * 根据内容模式（sfw, r18, mix）构建请求API的URL
     */
    private String buildApiUrl(String tag, String contentMode, int num) {
        HttpUrl.Builder urlBuilder = Objects.requireNonNull(HttpUrl.parse(setuApiConfig.getUrl())).newBuilder();
        SetuApiConfig.Params params = setuApiConfig.getParams();

        if (params.getNum() != null && params.getNum().getKey() != null) {
            urlBuilder.addQueryParameter(params.getNum().getKey(), String.valueOf(num));
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
                    result = JsonPath.read(jsonBody, setuApiConfig.getResponse().getR18().getJsonPath());
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
        List<String> urls = response.imgUrls();
        if (urls.isEmpty()) {
            return;
        }

        boolean sendAsPdf = response.enabledR18();

        if (sendAsPdf) {
            List<Path> imagePaths = new ArrayList<>();
            for (String url : urls) {
                Path path = getOrDownloadImageFile(url);
                if (path != null) {
                    imagePaths.add(path);
                }
            }
            if (!imagePaths.isEmpty()) {
                sendAsPdfFile(bot, event, imagePaths);
            } else {
                throw new BotException("未能获取到任何图片文件以生成PDF");
            }
        } else {
            if (urls.size() > 1) {
                sendFowardedImages(bot, userId, groupId, urls);
            } else {
                sendSingleImage(bot, event, userId, groupId, urls.getFirst());
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

    /**
     * 发送单张图片。
     * 优先从缓存获取，否则下载并发送。下载后的图片会异步存入缓存。
     */
    public void sendSingleImage(Bot bot, AnyMessageEvent event, Long userId, Long groupId, String url) {
        // 使用CompletableFuture以非阻塞方式获取图片
        getImage(url).thenAccept(imageSource -> {
            if (imageSource != null) {
                doSendImage(bot, event, groupId, userId, imageSource);
            }
        }).exceptionally(ex -> {
            log.error("获取或发送单张图片失败: {}", url, ex);
            return null;
        });
    }

    /**
     * 以合并转发的方式发送多张图片。
     * 会并发下载所有图片，然后合并发送。
     */
    public void sendFowardedImages(Bot bot, Long userId, Long groupId, List<String> urls) {
        try (ExecutorService executorService = Executors.newVirtualThreadPerTaskExecutor()) {
            // 并发获取所有图片
            List<CompletableFuture<ImageSource>> futures = urls.stream()
                    .map(url -> getImage(url, executorService))
                    .toList();

            // 等待所有图片下载完成
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

            // 从future结果中提取图片消息字符串
            List<String> msgs = futures.stream()
                    .map(CompletableFuture::join)
                    .filter(Objects::nonNull)
                    .map(this::buildImageMsg)
                    .toList();

            if (msgs.isEmpty()) {
                log.warn("没有可发送的图片, URL列表: {}", urls);
                return;
            }

            // 生成转发消息体
            List<Map<String, Object>> forwardMsg = ShiroUtils.generateForwardMsg(
                    bot.getSelfId(),
                    bot.getLoginInfo().getData().getNickname(),
                    msgs
            );

            // 执行带重试的发送操作
            int retCode;
            ActionData<MsgId> resp;
            if (groupId != null) {
                resp = bot.sendGroupForwardMsg(groupId, forwardMsg);
            } else {
                resp = bot.sendPrivateForwardMsg(userId, forwardMsg);
            }
            retCode = resp.getRetCode();
            if (retCode != 0) {
                throw new BusinessException("合并转发图片发送失败，返回码: " + retCode);
            }
        }
    }

    /**
     * 核心图片获取逻辑：先检查缓存，如果未命中则从URL下载。
     *
     * @param url 图片URL
     * @return CompletableFuture<ImageSource> 包含图片源的异步结果
     */
    private CompletableFuture<ImageSource> getImage(String url) {
        // 默认在ForkJoinPool的公共池中执行
        return getImage(url, Executors.newVirtualThreadPerTaskExecutor());
    }

    private CompletableFuture<ImageSource> getImage(String url, Executor executor) {
        return CompletableFuture.supplyAsync(() -> {
            String cacheKey = generateCacheKeyFromUrl(url);
            Path imagePath = fileStorageService.getFilePathByCacheKey(cacheKey);

            if (imagePath != null) {
                log.info("缓存命中: {}", imagePath);
                return new ImageSource(imagePath.toUri());
            }

            log.info("缓存未命中，下载: {}", url);
            Request request = new Request.Builder().url(url).build();
            // 使用重试模板执行下载
            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful() || response.body() == null) {
                    throw new IOException("图片下载失败, URL: " + url + ", Code: " + response.code());
                }
                byte[] imageBytes = response.body().bytes();
                // 异步缓存
                CompletableFuture.runAsync(() ->
                        fileStorageService.saveFileByCacheKey(cacheKey, imageBytes, Duration.ofHours(1))
                );
                return new ImageSource(imageBytes);
            } catch (IOException e) {
                log.error("图片下载异常: URL={}", url, e);
                return null;
            }
        }, executor);
    }

    /**
     * 底层发送图片的统一方法。
     */
    private void doSendImage(Bot bot, AnyMessageEvent event, Long groupId, Long userId, ImageSource imageSource) {
        String msg = buildImageMsg(imageSource);
        if (event != null) {
            SendMsgUtil.sendMsgByEvent(bot, event, msg, false);
        } else if (groupId != null) {
            SendMsgUtil.sendGroupMsg(bot, groupId, msg, false);
        } else {
            SendMsgUtil.sendPrivateMsg(bot, userId, msg, false);
        }
    }

    /**
     * 根据图片源构建Mirai消息字符串。
     */
    private String buildImageMsg(ImageSource imageSource) {
        MsgUtils builder = MsgUtils.builder();
        if (imageSource.isBytes()) {
            return builder.img(imageSource.bytes).build();
        } else {
            return builder.img(imageSource.uri.toString()).build();
        }
    }


    private void sendAsPdfFile(Bot bot, AnyMessageEvent event, java.util.List<Path> imagePaths) {
        final Path pdfPath = PdfUtil.wrapImageIntoPdf(imagePaths, fileStorageProperties.getLocal().getBasePath() + File.separator + "setu_tmp");
        if (pdfPath == null) {
            throw new BotException("生成PDF文件失败");
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
