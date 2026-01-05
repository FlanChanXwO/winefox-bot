package com.github.winefoxbot.plugins.setu;

import cn.hutool.core.util.URLUtil;
import com.github.winefoxbot.core.annotation.Plugin;
import com.github.winefoxbot.core.annotation.PluginFunction;
import com.github.winefoxbot.core.config.file.FileStorageProperties;
import com.github.winefoxbot.core.constants.ConfigConstants;
import com.github.winefoxbot.core.exception.bot.NetworkException;
import com.github.winefoxbot.core.exception.bot.ResourceNotFoundException;
import com.github.winefoxbot.core.exception.bot.SendMessageException;
import com.github.winefoxbot.core.manager.ConfigManager;
import com.github.winefoxbot.core.model.dto.SendMsgResult;
import com.github.winefoxbot.core.model.enums.Permission;
import com.github.winefoxbot.core.service.file.FileStorageService;
import com.github.winefoxbot.core.service.shiro.ShiroSessionStateService;
import com.github.winefoxbot.core.utils.BotUtils;
import com.github.winefoxbot.core.utils.FileUtil;
import com.github.winefoxbot.core.utils.PdfUtil;
import com.github.winefoxbot.plugins.setu.config.SetuApiConfig;
import com.github.winefoxbot.plugins.setu.model.dto.SetuApiResponse;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.PathNotFoundException;
import com.mikuac.shiro.annotation.AnyMessageHandler;
import com.mikuac.shiro.annotation.MessageHandlerFilter;
import com.mikuac.shiro.annotation.common.Order;
import com.mikuac.shiro.annotation.common.Shiro;
import com.mikuac.shiro.common.utils.MsgUtils;
import com.mikuac.shiro.core.Bot;
import com.mikuac.shiro.dto.event.message.AnyMessageEvent;
import com.mikuac.shiro.dto.event.message.GroupMessageEvent;
import com.mikuac.shiro.enums.MsgTypeEnum;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;

@Plugin(
        name = "娱乐功能",
        description = "提供娱乐方式",
        permission = Permission.USER,
        iconPath = "icon/娱乐功能.png",
        order = 7
)
@Component
@Shiro
@Slf4j
@RequiredArgsConstructor
public class SetuPlugin {
    private final OkHttpClient httpClient;
    private final SetuApiConfig setuApiConfig;
    private final FileStorageService fileStorageService;
    private final FileStorageProperties fileStorageProperties;
    private final ShiroSessionStateService shiroSessionStateService;
    private final ConfigManager configManager;

    private static final int MAX_RETRIES = 3;
    private static final Duration IMAGE_CACHE_DURATION = Duration.ofHours(1);

    @Async
    @PluginFunction(
            name = "随机福利图片获取",
            description = "使用命令获取随机福利图片，可附加标签，如：来份碧蓝档案福利图",
            commands = {"来份色图", "来张色图", "来份[标签]瑟图", "来个[标签]福利图", "来份[标签]涩图", "来点[标签]色图", "来点[标签]瑟图", "来点[标签]涩图", "来点[标签]福利图"}
    )
    @Order(10)
    @AnyMessageHandler
    @MessageHandlerFilter(types = MsgTypeEnum.text, cmd = "^(来(份|个|张))(\\S*?)(福利|色|瑟|涩|塞|)图$")
    public void getRandomPicture(Bot bot, AnyMessageEvent event, Matcher matcher) {
        // 仅在私聊时进入命令模式，避免影响群聊体验
        if (event.getGroupId() == null) {
            String sessionKey = shiroSessionStateService.getSessionKey(event);
            shiroSessionStateService.enterCommandMode(sessionKey);
        }
        String tag = matcher.group(3); // 获取标签
        executeImageFetchingTask(bot, event, tag);
    }

    /**
     * 主任务执行逻辑：获取配置、构建URL、请求API、发送图片
     */
    private void executeImageFetchingTask(Bot bot, AnyMessageEvent event, String tag) {
        Long groupId = event.getGroupId();
        Long userId = event.getUserId();
        // 使用 ConfigManager.get 自动处理私聊/群聊/全局配置
        String contentMode = configManager.getOrDefault(ConfigConstants.AdultContent.SETU_CONTENT_MODE, userId, groupId, ConfigConstants.AdultContent.MODE_SFW);
        log.info("开始获取图片任务，标签: '{}', 内容模式: {}", tag, contentMode);

        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                String apiUrl = buildApiUrl(tag, contentMode);
                if (apiUrl == null) {
                    bot.sendMsg(event, "API配置不完整，无法构建请求。", false);
                    return;
                }
                log.info("第 {}/{} 次尝试, 请求URL: {}", attempt, MAX_RETRIES, apiUrl);

                SetuApiResponse imageUrl = fetchImageUrlFromApi(apiUrl);
                if (imageUrl == null) {
                    log.warn("第 {} 次尝试失败：未能从API获取到有效的图片URL。", attempt);
                    continue;
                }
                log.info("成功获取到图片URL: {}", imageUrl);

                // 将 contentMode 传递下去，用于决定发送方式
                sendImage(bot, event, imageUrl);
                return; // 成功后立即返回
            } catch (Exception e) {
                log.error("第 {}/{} 次尝试时发生异常", attempt, MAX_RETRIES, e);
            }
        }
        throw new NetworkException(bot, event, "尝试多次后仍未能获取到图片，请稍后再试~", null);
    }

    /**
     * 根据内容模式（sfw, r18, mix）构建请求API的URL
     */
    private String buildApiUrl(String tag, String contentMode) {
        HttpUrl.Builder urlBuilder = HttpUrl.parse(setuApiConfig.getUrl()).newBuilder();
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
                case ConfigConstants.AdultContent.MODE_R18: // 仅r18模式
                    if (r18Config.getR18ModeValue() != null) {
                        urlBuilder.addQueryParameter(r18Config.getKey(), r18Config.getR18ModeValue());
                    }
                    break;
                case ConfigConstants.AdultContent.MODE_SFW: // 仅sfw模式
                    if (r18Config.getSafeModeValue() != null) {
                        urlBuilder.addQueryParameter(r18Config.getKey(), r18Config.getSafeModeValue());
                    }
                    break;
                case ConfigConstants.AdultContent.MODE_MIX: // 混合模式
                default:
                    if (r18Config.getMixModeValue() != null) {
                        urlBuilder.addQueryParameter(r18Config.getKey(), r18Config.getMixModeValue());
                    }
                    break;
            }
        }
        if (tag != null && !tag.isBlank() && params.getTag() != null && params.getTag().getKey() != null) {
            urlBuilder.addQueryParameter(params.getTag().getKey(), tag);
        }

        return urlBuilder.build().toString();
    }

    /**
     * 从API获取图片URL，支持直接图片响应和JSON响应
     */
    private SetuApiResponse fetchImageUrlFromApi(String url) throws IOException {
        Request request = new Request.Builder().url(url).build();

        if (SetuApiConfig.ResponseType.IMAGE.equals(setuApiConfig.getResponseType())) {
            return new SetuApiResponse(url, true);
        }

        if (SetuApiConfig.ResponseType.JSON.equals(setuApiConfig.getResponseType())) {
            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful() || response.body() == null) {
                    log.error("请求API失败, Code: {}, URL: {}", response.code(), url);
                    return null;
                }
                String jsonBody = response.body().string();
                try {
                    String imageUrl = JsonPath.read(jsonBody, setuApiConfig.getJsonPath());
                    Boolean enabledR18 = JsonPath.read(jsonBody, setuApiConfig.getResponse().getR18().getJsonPath());
                    if (imageUrl == null || imageUrl.isBlank()) {
                        log.warn("JsonPath '{}' 解析结果为空. 响应体: {}", setuApiConfig.getJsonPath(), jsonBody);
                        return null;
                    }
                    return new SetuApiResponse(imageUrl, enabledR18);
                } catch (PathNotFoundException e) {
                    log.error("根据 JsonPath '{}' 未找到图片URL. 请检查路径配置. 响应体: {}", setuApiConfig.getJsonPath(), jsonBody, e);
                    return null;
                } catch (Exception e) {
                    log.error("解析API响应JSON时发生错误. 响应体: {}", jsonBody, e);
                    return null;
                }
            }
        }
        log.warn("未知的 response-type: '{}', 无法处理API响应.", setuApiConfig.getResponseType());
        return null;
    }

    /**
     * 统一处理图片发送逻辑，包括缓存检查、下载和根据模式选择发送方式
     */
    private void sendImage(Bot bot, AnyMessageEvent event, SetuApiResponse imageUrl) throws IOException {
        String cacheKey = generateCacheKeyFromUrl(imageUrl.getImgUrl());
        Path imagePath = fileStorageService.getFilePathByCacheKey(cacheKey);
        // R18模式下，总是作为PDF文件发送
        boolean sendAsPdf = imageUrl.getEnabledR18();

        // 缓存命中
        if (imagePath != null) {
            log.info("缓存命中！从本地文件加载图片: {}", imagePath);
            if (sendAsPdf) {
                sendAsPdfFile(bot, event, imagePath);
            } else {
                bot.sendMsg(event, MsgUtils.builder().img(imagePath.toUri().toString()).build(), false);
            }
            return;
        }

        // 缓存未命中，下载
        log.info("缓存未命中，开始下载图片: {}", imageUrl);
        Request imageRequest = new Request.Builder().url(imageUrl.getImgUrl()).build();
        try (Response imageResponse = httpClient.newCall(imageRequest).execute()) {
            if (!imageResponse.isSuccessful() || imageResponse.body() == null) {
                throw new NetworkException(bot, event, "图片获取失败 (" + imageResponse.code() + ")", null);
            }
            byte[] imageBytes = imageResponse.body().bytes();

            // 发送
            if (sendAsPdf) {
                sendImageBytesAsPdf(bot, event, imageBytes);
            } else {
                bot.sendMsg(event, MsgUtils.builder().img(imageBytes).build(), false);
            }
            log.info("图片发送成功！");

            // 异步缓存
            CompletableFuture.runAsync(() -> {
                log.info("开始异步缓存图片...");
                fileStorageService.saveFileByCacheKey(cacheKey, imageBytes, IMAGE_CACHE_DURATION);
                log.info("图片异步缓存成功！CacheKey: {}", cacheKey);
            });
        }
    }

    /**
     * 将给定的图片字节数组包装成PDF发送（用于处理下载后的R18图片）
     */
    private void sendImageBytesAsPdf(Bot bot, AnyMessageEvent event, byte[] imageBytes) {
        Path tempFile = null;
        try {
            tempFile = Files.createTempFile("temp_image_", ".jpg");
            Files.write(tempFile, imageBytes);
            sendAsPdfFile(bot, event, tempFile);
        } catch (IOException e) {
            log.error("创建临时文件以发送PDF时失败", e);
            throw new SendMessageException(bot, event, "处理R18图片时出错", e);
        } finally {
            if (tempFile != null) {
                try {
                    Files.deleteIfExists(tempFile);
                } catch (IOException e) {
                    log.warn("删除临时图片文件失败: {}", tempFile, e);
                }
            }
        }
    }

    /**
     * 将给定的图片文件路径包装成PDF发送，并处理撤回逻辑
     */
    private void sendAsPdfFile(Bot bot, AnyMessageEvent event, Path imagePath) throws IOException {
        final Path pdfPath = PdfUtil.wrapImageIntoPdf(List.of(imagePath), fileStorageProperties.getLocal().getBasePath() + File.separator + "setu_tmp");
        if (pdfPath == null) {
            throw new ResourceNotFoundException(bot, event, "PDF文件生成失败", null);
        }
        String fileName = pdfPath.getFileName().toString();
        log.info("准备上传PDF文件: {}", pdfPath);
        Long groupId = event.getGroupId();
        Long userId = event.getUserId();
        CompletableFuture<SendMsgResult> sendFuture = BotUtils.uploadFileAsync(bot, event, pdfPath, fileName);
        sendFuture.whenCompleteAsync((result, throwable) -> {
            // 仅在群聊中且配置开启时，才进行撤回操作
            if (event instanceof GroupMessageEvent groupEvent && event.getGroupId() != null) {
                boolean autoRevoke = configManager.getOrDefault(ConfigConstants.AdultContent.ADULT_AUTO_REVOKE_ENABLED, userId, groupId, true);
                if (result != null && result.isSuccess() && autoRevoke) {
                    deleteGroupFile(bot, groupEvent, fileName);
                }
            }
            FileUtil.deleteFileWithRetry(pdfPath.toAbsolutePath().toString());
        });
    }

    /**
     * 在指定延迟后，删除群文件
     */
    private void deleteGroupFile(Bot bot, GroupMessageEvent groupEvent, String fileName) {
        try {
            // 撤回延迟时间也可以通过ConfigManager配置
            Long groupId = groupEvent.getGroupId();
            Long userId = groupEvent.getUserId();
            int delay = configManager.getOrDefault(ConfigConstants.AdultContent.ADULT_REVOKE_DELAY_SECONDS, userId, groupId, 30);
            TimeUnit.SECONDS.sleep(delay);
            BotUtils.deleteGroupFile(bot, groupEvent, fileName);
            log.info("群组 {} 已根据配置自动撤回文件: {}", groupEvent.getGroupId(), fileName);
        } catch (InterruptedException e) {
            log.error("撤回群文件时线程被中断", e);
            Thread.currentThread().interrupt();
        }
    }

    /**
     * 从图片URL生成一个安全的文件名作为缓存键
     */
    private String generateCacheKeyFromUrl(String imageUrl) {
        try {
            URL url = URLUtil.url(imageUrl);
            String path = url.getPath();
            // 替换所有非字母、数字、点、和连字符的字符为下划线，以创建安全的文件名
            return "setu/" + path.replaceAll("[^a-zA-Z0-9.-]", "_");
        } catch (Exception e) {
            // 如果URL无效或解析失败，使用其哈希码作为后备方案
            return "setu/" + imageUrl.hashCode();
        }
    }
}
