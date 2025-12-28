package com.github.winefoxbot.plugins;

import cn.hutool.core.bean.BeanUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.winefoxbot.annotation.PluginFunction;
import com.github.winefoxbot.config.SetuApiConfig;
import com.github.winefoxbot.config.WineFoxBotConfig;
import com.github.winefoxbot.manager.SemaphoreManager;
import com.github.winefoxbot.model.entity.SetuConfig;
import com.github.winefoxbot.model.enums.Permission;
import com.github.winefoxbot.model.enums.SessionType;
import com.github.winefoxbot.service.setu.SetuConfigService;
import com.github.winefoxbot.utils.BotUtils;
import com.github.winefoxbot.utils.PdfUtil;
import com.mikuac.shiro.annotation.AnyMessageHandler;
import com.mikuac.shiro.annotation.MessageHandlerFilter;
import com.mikuac.shiro.annotation.common.Shiro;
import com.mikuac.shiro.common.utils.MsgUtils;
import com.mikuac.shiro.core.Bot;
import com.mikuac.shiro.dto.action.common.ActionData;
import com.mikuac.shiro.dto.action.common.ActionRaw;
import com.mikuac.shiro.dto.action.response.GroupFilesResp;
import com.mikuac.shiro.dto.event.message.AnyMessageEvent;
import com.mikuac.shiro.enums.MsgTypeEnum;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;

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
    private final ObjectMapper objectMapper;
    private final SetuApiConfig setuApiConfig;
    private final SetuConfigService setuConfigService;
    private final SemaphoreManager semaphoreManager;
    @PluginFunction(group = "瑟瑟功能", name = "解除限制开关", description = "解除限制", permission = Permission.ADMIN, commands = {"/解除瑟瑟限制", "/开启瑟瑟限制"})
    @AnyMessageHandler
    @MessageHandlerFilter(types = MsgTypeEnum.text, cmd = "^" + WineFoxBotConfig.COMMAND_PREFIX_REGEX + "(解除瑟瑟限制|开启瑟瑟限制)" + "$")
    public void toggleR18(Bot bot, AnyMessageEvent event) {
        String msg = event.getMessage().replace(WineFoxBotConfig.COMMAND_PREFIX_REGEX, "");
        Long groupId = event.getGroupId();
        SetuConfig config = setuConfigService.getOrCreateSetuConfig(groupId, SessionType.fromValue(event.getMessageType()));
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

    @PluginFunction(group = "瑟瑟功能", name = "自动撤回开关", description = "开启或者关闭自动撤回在奇怪分级", permission = Permission.ADMIN, commands = {"/开启自动撤回", "/关闭自动撤回"})
    @AnyMessageHandler
    @MessageHandlerFilter(types = MsgTypeEnum.text, cmd = "^" + WineFoxBotConfig.COMMAND_PREFIX_REGEX + "(开启自动撤回|关闭自动撤回)" + "$")
    public void toggleAutoRevoke(Bot bot, AnyMessageEvent event) {
        String msg = event.getMessage().replace(WineFoxBotConfig.COMMAND_PREFIX_REGEX, "");
        Long groupId = event.getGroupId();
        SetuConfig config = setuConfigService.getOrCreateSetuConfig(groupId, SessionType.fromValue(event.getMessageType()));
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
        bot.sendMsg(event, updated ? "设置已更新，当前自动撤回状态：" + (config.getR18Enabled() ? "开启" : "关闭") : "设置更新失败，请重试", false);
    }

    @PluginFunction(
            group = "瑟瑟功能",
            name = "设置并发请求数",
            description = "设置当前会话（群/私聊）允许同时获取图片的最大数量",
            permission = Permission.ADMIN, // 仅管理员可用
            commands = {"/设置瑟瑟并发数 [数量]", "/设置色色并发数 [数量]"}
    )
    @AnyMessageHandler
    @MessageHandlerFilter(types = MsgTypeEnum.text, cmd = "^" + WineFoxBotConfig.COMMAND_PREFIX_REGEX + "设置(瑟瑟|色色)并发数\\s+(\\d+)$")
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
            log.info("Session [{}] max requests updated to {}", buildKey(sessionId, sessionType), newMaxRequests);
        } else {
            bot.sendMsg(event, "设置失败，请稍后重试或联系管理员。", false);
        }
    }


    @Async
    @PluginFunction(
            group = "瑟瑟功能",
            name = "随机福利图片获取",
            description = "使用命令获取随机色图，可附加标签，如：来份碧蓝档案色图",
            commands = {"来份色图", "来个色图", "来份涩图", "来个涩图", "来份瑟图", "来个瑟图", "来份塞图", "来个塞图", "来份[标签]色图"}
    )
    @AnyMessageHandler
    @MessageHandlerFilter(types = MsgTypeEnum.text, cmd = "^/?(来(份|个))(\\S*?)(色|瑟|涩|塞|)图$")
    public void getRandomPicture(Bot bot, AnyMessageEvent event, Matcher matcher) {
        Long sessionId = BotUtils.getSessionId(event);
        SessionType sessionType = SessionType.fromValue(event.getMessageType());
        SetuConfig config = setuConfigService.getOrCreateSetuConfig(sessionId, sessionType);
        int maxRequests = config.getMaxRequestInSession();
        String key = buildKey(sessionId, sessionType);
        Semaphore semaphore = semaphoreManager.getSemaphore(key, maxRequests);
        if (semaphore.tryAcquire()) {
            try {
                executeTask(bot, event, matcher,config.getR18Enabled());
            } finally {
                semaphore.release();
            }
        } else {
            bot.sendMsg(event, "命令使用太频繁，请稍后再试~", false);
        }
    }

    private void executeTask(Bot bot, AnyMessageEvent event, Matcher matcher, boolean enableR18) {
        Long groupId = event.getGroupId();
        boolean isInGroup = groupId != null;
        String tag = matcher.group(3); // 获取参数
        if (tag != null && !tag.isEmpty()) {
            log.info("收到了带标签的请求，标签: {}", tag);
        } else {
            log.info("收到了不带标签的随机图片请求");
        }

        String filePath = null;
        String fileName = null;
        try {
            List<SetuApiConfig.Api> apis = setuApiConfig.getApis();
            if (apis.isEmpty()) {
                bot.sendMsg(event, "未配置任何图片 API", false);
                return;
            }
            SetuApiConfig.Api selectedApi = apis.get((int) (Math.random() * apis.size()));
            HttpUrl.Builder builder = HttpUrl.parse(selectedApi.getUrl())
                    .newBuilder()
                    .addQueryParameter("num", "1") // 请求一张图片
                    .addQueryParameter("excludeAI", "true");
            if (enableR18) {
                builder.addQueryParameter(selectedApi.getR18().getKey(), selectedApi.getR18().getTrueValue());
            }
            if (tag != null) {
                builder.addQueryParameter("tag", tag);
            }
            String setuUrl = builder.build().toString();
            log.info("Using API URL: {}", setuUrl);
            SetuApiConfig.Api apiWithParams = new SetuApiConfig.Api();
            BeanUtil.copyProperties(selectedApi, apiWithParams);
            apiWithParams.setUrl(setuUrl);
            byte[] image = fetchImage(apiWithParams);
            if (image.length == 0) {
                bot.sendMsg(event, "未能获取到图片，请稍后再试~", false);
                return;
            }
            if (enableR18) {
                String s = PdfUtil.wrapByteImagesIntoPdf(List.of(image), "setu");
                if (s == null) {
                    bot.sendMsg(event, "整理图片时出错，请稍后再试~", false);
                    return;
                }
                filePath = s.replace("\\", "/");
                fileName = Path.of(s).getFileName().toString();
                log.info("Attempting to upload file: path='{}', name='{}'", filePath, fileName);
                ActionRaw actionRaw = isInGroup ? bot.uploadGroupFile(event.getGroupId(), filePath, fileName) : bot.uploadPrivateFile(event.getUserId(), filePath, fileName);
                if (actionRaw == null || actionRaw.getRetCode() != 0) {
                    log.error("File upload failed. Path: {}, Name: {}. Response: {}", filePath, fileName, actionRaw);
                }
            } else {
                bot.sendMsg(event, MsgUtils.builder().img(image).build(),false);
            }
        } catch (Exception e) {
            log.error("Error during R18 picture fetch/upload", e);
            bot.sendMsg(event, "获取图片时出错，请稍后再试~", false);
        } finally {
            if (enableR18) {
                if (isInGroup) {
                    deleteGroupFile(bot, event, fileName);
                }
                recycleTempFile(filePath);
            }
        }
    }


    @Async
    protected void deleteGroupFile(Bot bot, AnyMessageEvent anyMessageEvent, String fileName) {
        try {
            TimeUnit.SECONDS.sleep(20);
            Long groupId = anyMessageEvent.getGroupId();
            ActionData<GroupFilesResp> groupRootFiles = bot.getGroupRootFiles(groupId);
            GroupFilesResp data = groupRootFiles.getData();
            for (GroupFilesResp.Files file : data.getFiles()) {
                if (file.getFileName().equals(fileName)) {
                    bot.deleteGroupFile(groupId, file.getFileId(), file.getBusId());
                    break;
                }
            }
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

    }

    @Async
    protected void recycleTempFile(String filePath) {
        if (filePath != null && Files.exists(Path.of(filePath))) {
            try {
                Files.delete(Path.of(filePath));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }


    private byte[] fetchImage(SetuApiConfig.Api api) throws IOException {
        Request request = new Request.Builder()
                .url(api.getUrl()).build();

        if ("json".equalsIgnoreCase(api.getResponseType())) {
            Response jsonResponse = httpClient.newCall(request).execute();

            try {
                if (!jsonResponse.isSuccessful()) {
                    throw new IOException("Request for JSON metadata failed with code " + jsonResponse.code());
                }
                ResponseBody jsonBody = jsonResponse.body();
                if (jsonBody == null) {
                    throw new IOException("Received empty response body for JSON metadata");
                }

                // 读取响应体，这会消耗并关闭流
                String bodyString = jsonBody.string();

                // 解析 JSON
                JsonNode node = objectMapper.readTree(bodyString);
                String[] paths = api.getJsonPath().split("\\.");
                log.info("Parsing JSON path: {}", Arrays.toString(paths));
                for (String path : paths) {
                    node = node.get(path);
                    if (node.isArray()) {
                        node = node.get(0);
                    }
                    if (node == null) {
                        log.error("Invalid JSON path: {}", api.getJsonPath());
                        log.error("Response body: {}", bodyString);
                        throw new IOException("Invalid JSON path");
                    }
                }
                String imageUrl = node.asText();
                log.info("Fetched image URL: {}", imageUrl);

                Request imageRequest = new Request.Builder().url(imageUrl).build();
                try (Response imageResponse = httpClient.newCall(imageRequest).execute()) {
                    if (!imageResponse.isSuccessful()) {
                        throw new IOException("Failed to download image from " + imageUrl + " with code " + imageResponse.code());
                    }
                    ResponseBody imageBody = imageResponse.body();
                    if (imageBody == null) {
                        throw new IOException("Received empty response body for image download");
                    }
                    return imageBody.bytes();
                }

            } finally {
                jsonResponse.close();
            }

        } else {
            // 对于直接的图片响应，原来的 try-with-resources 写法是完美的
            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    throw new IOException("Request for direct image failed with code " + response.code());
                }
                ResponseBody body = response.body();
                if (body == null) {
                    throw new IOException("Received empty response body for direct image");
                }
                return body.bytes();
            }
        }
    }

    private String buildKey(Long sessionId, SessionType sessionType) {
        return sessionId + "_" + sessionType.getValue();
    }


}