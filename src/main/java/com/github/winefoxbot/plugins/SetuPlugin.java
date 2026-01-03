package com.github.winefoxbot.plugins;

import cn.hutool.core.util.URLUtil;
import com.github.winefoxbot.annotation.PluginFunction;
import com.github.winefoxbot.config.file.FileStorageProperties;
import com.github.winefoxbot.config.setu.SetuApiConfig;
import com.github.winefoxbot.exception.bot.NetworkException;
import com.github.winefoxbot.exception.bot.ResourceNotFoundException;
import com.github.winefoxbot.exception.bot.SendMessageException;
import com.github.winefoxbot.manager.SemaphoreManager;
import com.github.winefoxbot.model.dto.shiro.SendMsgResult;
import com.github.winefoxbot.model.entity.SetuConfig;
import com.github.winefoxbot.model.enums.Permission;
import com.github.winefoxbot.model.enums.SessionType;
import com.github.winefoxbot.service.file.FileStorageService;
import com.github.winefoxbot.service.setu.SetuConfigService;
import com.github.winefoxbot.service.shiro.ShiroSessionStateService;
import com.github.winefoxbot.utils.BotUtils;
import com.github.winefoxbot.utils.FileUtil;
import com.github.winefoxbot.utils.PdfUtil;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.PathNotFoundException;
import com.mikuac.shiro.annotation.AnyMessageHandler;
import com.mikuac.shiro.annotation.MessageHandlerFilter;
import com.mikuac.shiro.annotation.common.Order;
import com.mikuac.shiro.annotation.common.Shiro;
import com.mikuac.shiro.common.utils.MsgUtils;
import com.mikuac.shiro.core.Bot;
import com.mikuac.shiro.dto.action.common.ActionData;
import com.mikuac.shiro.dto.action.common.ActionRaw;
import com.mikuac.shiro.dto.action.response.GroupFilesResp;
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

import java.io.*;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;

import static com.github.winefoxbot.config.app.WineFoxBotConfig.COMMAND_PREFIX_REGEX;
import static com.github.winefoxbot.config.app.WineFoxBotConfig.COMMAND_SUFFIX_REGEX;

/**
 * @author FlanChan (badapple495@outlook.com)
 * @since 2025-12-09-1:44
 */
@Component
@Shiro
@Slf4j
@RequiredArgsConstructor
public class SetuPlugin {
    private final OkHttpClient httpClient;
    private final SetuApiConfig setuApiConfig;
    private final SetuConfigService setuConfigService;
    private final SemaphoreManager semaphoreManager;
    private final FileStorageService fileStorageService;
    private final FileStorageProperties fileStorageProperties;
    private final ShiroSessionStateService shiroSessionStateService;
    // 常量
    private static final int MAX_RETRIES = 3;
    private static final Duration IMAGE_CACHE_DURATION = Duration.ofHours(1); // 图片缓存1h

    @PluginFunction(group = "瑟瑟功能", name = "解除限制开关", description = "解除限制", permission = Permission.ADMIN, commands = {"/解除瑟瑟限制", "/开启瑟瑟限制"})
    @AnyMessageHandler
    @MessageHandlerFilter(types = MsgTypeEnum.text, cmd =  COMMAND_PREFIX_REGEX + "(解除瑟瑟限制|开启瑟瑟限制)" + COMMAND_SUFFIX_REGEX)
    public void toggleR18(Bot bot, AnyMessageEvent event) {
        String msg = event.getMessage().replace(COMMAND_PREFIX_REGEX, "");
        Long sessionId = BotUtils.getSessionId(event);
        SetuConfig config = setuConfigService.getOrCreateSetuConfig(sessionId, SessionType.fromValue(event.getMessageType()));
        Boolean r18Enabled = config.getR18Enabled();
        // 功能开关指令
        if ("解除瑟瑟限制".equals(msg) && r18Enabled) {
            bot.sendMsg(event, "R18已经开启了", false);
            return;
        } else if ("开启瑟瑟限制".equals(msg) && !r18Enabled) {
            bot.sendMsg(event, "R18已经关闭了", false);
            return;
        }
        boolean updated = setuConfigService.toggleR18Setting(config);
        bot.sendMsg(event, updated ? "设置已更新，当前R18状态：" + (config.getR18Enabled() ? "开启" : "关闭") : "设置更新失败，请重试", false);
    }

    @PluginFunction(group = "瑟瑟功能", name = "自动撤回奇怪图片开关", description = "开启或者关闭自动撤回在奇怪分级", permission = Permission.ADMIN, commands = {"/开启瑟瑟自动撤回", "/关闭瑟瑟自动撤回"})
    @AnyMessageHandler
    @MessageHandlerFilter(types = MsgTypeEnum.text, cmd =  COMMAND_PREFIX_REGEX + "(开启|关闭)瑟瑟自动撤回" + COMMAND_SUFFIX_REGEX)
    public void toggleAutoRevoke(Bot bot, AnyMessageEvent event) {
        String msg = event.getMessage().replace(COMMAND_PREFIX_REGEX, "");
        Long sessionId = BotUtils.getSessionId(event);
        SetuConfig config = setuConfigService.getOrCreateSetuConfig(sessionId, SessionType.fromValue(event.getMessageType()));
        Boolean autoRevoke = config.getAutoRevoke();
        // 功能开关指令
        if ("开启自动撤回".equals(msg) && autoRevoke) {
            bot.sendMsg(event, "自动撤回已经开启了", false);
            return;
        } else if ("关闭自动撤回".equals(msg) && !autoRevoke) {
            bot.sendMsg(event, "自动撤回关闭了", false);
            return;
        }
        boolean updated = setuConfigService.toggleAutoRevokeSetting(config);
        bot.sendMsg(event, updated ? "设置已更新，当前自动撤回状态：" + (config.getAutoRevoke() ? "开启" : "关闭") : "设置更新失败，请重试", false);
    }

    @PluginFunction(
            group = "瑟瑟功能",
            name = "设置并发请求数",
            description = "设置当前会话（群/私聊）允许同时获取图片的最大数量",
            permission = Permission.ADMIN, // 仅管理员可用
            commands = {"/设置瑟瑟并发数 [数量]", "/设置色色并发数 [数量]"}
    )
    @AnyMessageHandler
    @MessageHandlerFilter(types = MsgTypeEnum.text, cmd = COMMAND_PREFIX_REGEX + "设置(瑟瑟|色色)并发数\\s+(\\d+)$")
    public void setMaxRequests(Bot bot, AnyMessageEvent event, Matcher matcher) {
        String numStr = matcher.group(2);
        int newMaxRequests;
        try {
            newMaxRequests = Integer.parseInt(numStr);
            // 设定一个合理的范围，比如 1 到 10
            if (newMaxRequests < 1 || newMaxRequests > 10) {
                bot.sendMsg(event, "设置失败，数量必须在 1 到 10 之间。", false);
                return;
            }
        } catch (NumberFormatException e) {
            bot.sendMsg(event, "设置失败，请输入一个有效的数字。", false);
            return;
        }

        Long sessionId = BotUtils.getSessionId(event);
        SessionType sessionType = SessionType.fromValue(event.getMessageType());
        SetuConfig config = setuConfigService.getOrCreateSetuConfig(sessionId, sessionType);

        // 调用 Service 方法更新配置
        boolean success = setuConfigService.updateMaxRequests(config, newMaxRequests);

        if (success) {
            bot.sendMsg(event, "设置成功！当前会话的最大并发请求数已更新为：" + newMaxRequests, false);
            log.info("Session [{}] max requests updated to {}", sessionId, newMaxRequests);
        } else {
            bot.sendMsg(event, "设置失败，请稍后重试或联系管理员。", false);
        }
    }


    @Async
    @PluginFunction(
            group = "瑟瑟功能",
            name = "随机福利图片获取",
            description = "使用命令获取随机色图，可附加标签，如：来份碧蓝档案色图",
            commands = {"来份色图", "来个色图", "来份涩图", "来个涩图", "来份瑟图", "来个瑟图", "来份塞图", "来个塞图", "来份[标签]色图", "来个[标签]色图",
                    "来份[标签]涩图", "来个[标签]涩图", "来份[标签]瑟图", "来个[标签]瑟图", "来份[标签]塞图", "来个[标签]塞图"
            }
    )
    @Order(10)
    @AnyMessageHandler
    @MessageHandlerFilter(types = MsgTypeEnum.text, cmd = COMMAND_PREFIX_REGEX + "?(来(份|个|张))(\\S*?)(色|瑟|涩|塞|)图"+ COMMAND_SUFFIX_REGEX)
    public void getRandomPicture(Bot bot, AnyMessageEvent event, Matcher matcher) {
        String sessionKey = shiroSessionStateService.getSessionKey(event);
        shiroSessionStateService.enterCommandMode(sessionKey);
        Long sessionId = BotUtils.getSessionId(event);
        SessionType sessionType = SessionType.fromValue(event.getMessageType());
        SetuConfig config = setuConfigService.getOrCreateSetuConfig(sessionId, sessionType);
        String key = BotUtils.getSessionIdWithPrefix(event);
        Semaphore semaphore = semaphoreManager.getSemaphore(key, config.getMaxRequestInSession());
        try {
            if (semaphore.tryAcquire()) {
                try {
                    String tag = matcher.group(3); // 获取标签
                    executeImageFetchingTask(bot, event, tag, config);
                } finally {
                    semaphore.release();
                }
            } else {
                bot.sendMsg(event, "命令使用太频繁，请稍后再试~", false);
            }
        } finally {
            shiroSessionStateService.exitCommandMode(sessionKey);
        }
    }

    /**
     * 主任务执行逻辑：构建URL、获取图片、处理并发送
     */
    private void executeImageFetchingTask(Bot bot, AnyMessageEvent event, String tag, SetuConfig sessionConfig) {
        log.info("开始获取图片任务，标签: '{}', R18: {}", tag, sessionConfig.getR18Enabled());

        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                // 1. 构建请求URL
                String apiUrl = buildApiUrl(tag, sessionConfig.getR18Enabled());
                if (apiUrl == null) {
                    bot.sendMsg(event, "API配置不完整，无法构建请求。", false);
                    return;
                }
                log.info("第 {}/{} 次尝试, 请求URL: {}", attempt, MAX_RETRIES, apiUrl);

                // 2. 获取图片URL
                String imageUrl = fetchImageUrlFromApi(apiUrl);
                if (imageUrl == null || imageUrl.isBlank()) {
                    log.warn("第 {} 次尝试失败：未能从API获取到有效的图片URL。", attempt);
                    continue; // 进入下一次重试
                }
                log.info("成功获取到图片URL: {}", imageUrl);

                // 3. 下载或从缓存获取图片，并发送
                handleAndSendImage(bot, event, imageUrl, sessionConfig);

                return; // 成功获取并发送后，直接退出循环和方法
            } catch (Exception e) {
                log.error("第 {}/{} 次尝试时发生异常", attempt, MAX_RETRIES, e);
            }
        }

        // 所有重试都失败后
        throw new NetworkException(bot,event,"尝试多次后仍未能获取到图片，请稍后再试~");
    }

    /**
     * 使用新的 SetuApiConfig 构建请求 URL
     */
    private String buildApiUrl(String tag, boolean enableR18) {
        HttpUrl.Builder urlBuilder = HttpUrl.parse(setuApiConfig.getUrl()).newBuilder();
        SetuApiConfig.Params params = setuApiConfig.getParams();

        if (params.getNum() != null && params.getNum().getKey() != null) {
            urlBuilder.addQueryParameter(params.getNum().getKey(), params.getNum().getValue());
        }
        if (params.getExcludeAI() != null && params.getExcludeAI().getKey() != null) {
            urlBuilder.addQueryParameter(params.getExcludeAI().getKey(), params.getExcludeAI().getValue());
        }

        SetuApiConfig.R18Config r18Config = params.getR18();
        if (r18Config != null && r18Config.getKey() != null) {
            if (enableR18) {
                urlBuilder.addQueryParameter(r18Config.getKey(), r18Config.getTrueValue());
            } else if (r18Config.getFalseValue() != null) {
                urlBuilder.addQueryParameter(r18Config.getKey(), r18Config.getFalseValue());
            }
        }

        if (tag != null && !tag.isBlank() && params.getTag() != null && params.getTag().getKey() != null) {
            urlBuilder.addQueryParameter(params.getTag().getKey(), tag);
        }

        return urlBuilder.build().toString();
    }

    /**
     * 根据API配置，从接口获取图片最终的URL。
     * (已使用 com.jayway.jsonpath 库进行重构)
     *
     * @param apiUrl 构造好的API请求地址
     * @return 图片的URL，如果失败则返回null
     * @throws IOException 网络请求异常
     */
    private String fetchImageUrlFromApi(String apiUrl) throws IOException {
        Request request = new Request.Builder().url(apiUrl).build();

        // 检查配置，如果响应类型是 "image"，API URL 本身就是图片 URL
        if (SetuApiConfig.ResponseType.IMAGE.equals(setuApiConfig.getResponseType())) {
            return apiUrl;
        }

        // 仅当响应类型为 "json" 时执行以下逻辑
        if (SetuApiConfig.ResponseType.JSON.equals(setuApiConfig.getResponseType())) {
            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful() || response.body() == null) {
                    log.error("请求API失败, Code: {}, URL: {}", response.code(), apiUrl);
                    return null;
                }
                String jsonBody = response.body().string();

                try {
                    // 使用 Jayway JsonPath 库来读取 JSON
                    // read 方法会根据 JSONPath 表达式精确查找
                    // 它能正确处理 $.data[0].urls.original 这样的复杂路径
                    String imageUrl = JsonPath.read(jsonBody, setuApiConfig.getJsonPath());
                    // 校验获取到的URL是否有效
                    if (imageUrl == null || imageUrl.isBlank()) {
                        log.warn("JsonPath '{}' 解析结果为空. 响应体: {}", setuApiConfig.getJsonPath(), jsonBody);
                        return null;
                    }
                    return imageUrl;

                } catch (PathNotFoundException e) {
                    // 当 JsonPath 找不到对应的路径时，会抛出此异常，这是最常见的错误
                    log.error("根据 JsonPath '{}' 未找到图片URL. 请检查路径配置是否正确. 响应体: {}", setuApiConfig.getJsonPath(), jsonBody, e);
                    return null;
                } catch (Exception e) {
                    // 捕获其他可能的解析异常，例如JSON格式本身有问题
                    log.error("解析API响应JSON时发生错误. 响应体: {}", jsonBody, e);
                    return null;
                }
            }
        }

        // 如果 responseType 配置既不是 "json" 也不是 "image"，则返回 null
        log.warn("未知的 response-type: '{}', 无法处理API响应.", setuApiConfig.getResponseType());
        return null;
    }


    private void handleAndSendImage(Bot bot, AnyMessageEvent event, String imageUrl, SetuConfig sessionConfig) throws IOException {
        // 1. 生成基于图片URL的唯一缓存键
        String cacheKey = generateCacheKeyFromUrl(imageUrl);

        // 2. 优先尝试从缓存获取文件路径 (这部分逻辑不变，缓存命中时速度最快)
        Path imagePath = fileStorageService.getFilePathByCacheKey(cacheKey);
        if (imagePath != null) {
            log.info("缓存命中！从本地文件加载图片: {}", imagePath);
            sendImage(bot, event, imagePath, sessionConfig); // 封装发送逻辑
            return;
        }

        // 3. 缓存未命中，流式下载、发送并异步缓存
        log.info("缓存未命中，开始流式处理图片: {}", imageUrl);
        Request imageRequest = new Request.Builder().url(imageUrl).build();
        try (Response imageResponse = httpClient.newCall(imageRequest).execute()) {
            if (!imageResponse.isSuccessful() || imageResponse.body() == null) {
                bot.sendMsg(event, "图片获取失败了 >_< (" + imageResponse.code() + ")", false);
                return;
            }

            try (InputStream imageStream = imageResponse.body().byteStream()) {
                // 为了能同时发送和缓存，我们需要先将流读入内存。
                // 这是因为大部分机器人SDK的发送接口和我们的文件存储接口都需要一个完整的流，
                // 而一个流不能被同时消费两次。
                // ByteArrayOutputStream 是一个在内存中的输出流。
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                imageStream.transferTo(baos); // 将网络流的所有字节复制到内存中
                byte[] imageBytes = baos.toByteArray();

                // 立即使用内存中的字节数组发送图片
                log.info("图片已下载到内存，立即发送...");
                // 注意：这里的发送方法需要支持字节数组或其输入流
                // 假设 MsgUtils.builder().img() 支持从 InputStream 发送
                sendImageFromBytes(bot, event, imageBytes, sessionConfig);
                log.info("图片发送成功！");


                // 【异步】将图片存入缓存，这步不阻塞用户
                CompletableFuture.runAsync(() -> {
                    log.info("开始异步缓存图片...");
                    fileStorageService.saveFileByCacheKey(cacheKey, imageBytes, IMAGE_CACHE_DURATION);
                    log.info("图片异步缓存成功！CacheKey: {}", cacheKey);
                });
            }
        } catch (IOException e) {
            log.error("处理图片时发生IO异常: {}", imageUrl, e);
            bot.sendMsg(event, "图片处理出错了 T_T", false);
        }
    }

    // 封装一个私有方法用于从字节发送，提高代码复用性
    private void sendImageFromBytes(Bot bot, AnyMessageEvent event, byte[] imageBytes, SetuConfig sessionConfig) {
        if (sessionConfig.getR18Enabled()) {
            try {
                Path tempFile = Files.createTempFile("temp_image_", ".jpg");
                Files.write(tempFile, imageBytes);
                sendAsPdfFile(bot, event, tempFile);
                Files.deleteIfExists(tempFile);
            } catch(IOException e) {
                log.error("创建临时文件发送PDF失败", e);
            }
        } else {
            // 假设框架支持从InputStream发送
            try (ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(imageBytes)) {
                bot.sendMsg(event, MsgUtils.builder().img(byteArrayInputStream.readAllBytes()).build(), false);
            }  catch (IOException e) {
                throw new SendMessageException(bot,event,"发送图片时发生错误");
            }
        }
    }

    // 封装原有的从路径发送图片的逻辑
    private void sendImage(Bot bot, AnyMessageEvent event, Path imagePath, SetuConfig sessionConfig) {
        if (sessionConfig.getR18Enabled()) {
            try {
                sendAsPdfFile(bot, event, imagePath);
            } catch (IOException e) {
                throw new SendMessageException(bot,event,"发送PDF文件时发生错误");
            }
        } else {
            bot.sendMsg(event, MsgUtils.builder().img(imagePath.toUri().toString()).build(), false);
        }
    }

    /**
     * 将图片文件包装成PDF文件发送，并处理临时文件
     * (已优化为接收文件路径)
     */
    private void sendAsPdfFile(Bot bot, AnyMessageEvent event, Path imagePath) throws IOException {
        Path pdfPath = null;
        // 使用PdfUtil将图片文件转换为PDF文件路径
        // 注意：这里需要确保你的 PdfUtil 支持从文件路径创建PDF
        pdfPath = PdfUtil.wrapImageIntoPdf(List.of(imagePath), fileStorageProperties.getLocal().getBasePath() + File.separator + "setu_tmp");

        if (pdfPath == null) {
            throw new ResourceNotFoundException(bot,event,"PDF文件生成失败，请稍后再试~");
        }

        String fileName = pdfPath.getFileName().toString();
        log.info("准备上传PDF文件: {}", pdfPath);
        CompletableFuture<SendMsgResult> sendFuture = BotUtils.uploadFileAsync(bot, event, pdfPath, fileName);
        Path finalPdfPath = pdfPath;
        sendFuture.whenCompleteAsync((result, throwable) -> {
            // 无论成功与否，都删除本地临时生成的PDF文件
            FileUtil.deleteFileWithRetry(finalPdfPath.toAbsolutePath().toString());
            if (result.isSuccess()) {
                deleteGroupFile(bot, event, fileName);
            }
        });
    }


    private void deleteGroupFile(Bot bot, GroupMessageEvent groupMessageEvent, String fileName) {
        try {
            TimeUnit.SECONDS.sleep(30);
            BotUtils.deleteGroupFile(bot, groupMessageEvent, fileName);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 从图片URL生成一个安全的文件名作为缓存键。
     */
    private String generateCacheKeyFromUrl(String imageUrl) {
        try {
            URL url = URLUtil.url(imageUrl);
            // 提取路径部分，并替换掉所有非字母数字的字符
            String path = url.getPath();
            return "setu/" + path.replaceAll("[^a-zA-Z0-9.-]", "_");
        } catch (Exception e) {
            // 如果URL无效，使用其哈希码作为后备
            return "setu/" + imageUrl.hashCode();
        }
    }

}