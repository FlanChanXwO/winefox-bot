package com.github.winefoxbot.plugins;

import cn.hutool.core.collection.ConcurrentHashSet;
import cn.hutool.core.util.RandomUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.winefoxbot.annotation.PluginFunction;
import com.github.winefoxbot.config.SetuConfig;
import com.github.winefoxbot.utils.PdfUtil;
import com.mikuac.shiro.annotation.AnyMessageHandler;
import com.mikuac.shiro.annotation.MessageHandlerFilter;
import com.mikuac.shiro.annotation.common.Shiro;
import com.mikuac.shiro.common.utils.MsgUtils;
import com.mikuac.shiro.common.utils.ShiroUtils;
import com.mikuac.shiro.core.Bot;
import com.mikuac.shiro.dto.action.common.ActionData;
import com.mikuac.shiro.dto.action.common.ActionRaw;
import com.mikuac.shiro.dto.action.response.GroupFilesResp;
import com.mikuac.shiro.dto.event.message.AnyMessageEvent;
import com.mikuac.shiro.enums.MsgTypeEnum;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
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
    private final SetuConfig setuConfig;
    private final Set<String> isFetchingSet = new ConcurrentHashSet<>();

    /**
     * 支持多种参数形式：
     * /setu → 随机
     * /setu 1 → 根据 ID
     * /setu 1 anime → ID + category
     * /setu anime → 根据分类随机
     * /setu cute → description 查找分类
     */
    @PluginFunction(
            group = "瑟瑟功能",
            name = "随机福利（不包含18）图片获取",
            description = "使用 /setu [id]|[分类] 命令获取随机图片，参数可选ID或分类。无参数则随机抽图",
            commands = {"/setu [id]|[分类]"}
    )
    @AnyMessageHandler
    @MessageHandlerFilter(types = MsgTypeEnum.text, cmd = "^/setu(?:\\s+(\\S+))?$")
    public void getRandomPicture(Bot bot, AnyMessageEvent event, Matcher matcher) {
        Long groupId = event.getGroupId();
        String fetchKey = groupId != null ? "group_" + groupId : "private_" + event.getUserId();
        if (isFetchingSet.contains(fetchKey)) {
            bot.sendMsg(event, "上一张图片还在加载中，请稍等~", false);
            return;
        }
        isFetchingSet.add(fetchKey);

        try {
            SetuConfig.Api selectedApi = null;
            String params = matcher.group(1);

            if (params != null && !params.isBlank()) {
                params = params.trim();
                String[] parts = params.split("\\s+");

                // 不能超过两个参数
                if (parts.length > 2) {
                    bot.sendMsg(event, "命令格式错误，正确格式：/setu [id] [category]", false);
                    return;
                }

                String p1 = parts[0];
                String p2 = parts.length > 1 ? parts[1] : null;

                // --------------------------
                // 情况 A：p1 是数字 → ID 方式
                // --------------------------
                if (p1.matches("\\d+")) {
                    int id = Integer.parseInt(p1);

                    selectedApi = setuConfig.getApis().stream()
                            .filter(api -> api.getId() == id)
                            .filter(api -> p2 == null || api.getCategory().equalsIgnoreCase(p2) && !api.getR18())
                            .findFirst()
                            .orElse(null);

                    if (selectedApi == null) {
                        bot.sendMsg(event, "未找到匹配的图片 API（ID 或 分类不正确）", false);
                        return;
                    }
                }

                // --------------------------
                // 情况 B：p1 是字符串 → 按分类或描述查找
                // --------------------------
                else {
                    String key = p1.toLowerCase();

                    // 先从 category 精确匹配
                    List<SetuConfig.Api> matchByCategory = setuConfig.getApis().stream()
                            .filter(api -> api.getCategory().equalsIgnoreCase(key))
                            .toList();

                    if (!matchByCategory.isEmpty()) {
                        selectedApi = RandomUtil.randomEle(matchByCategory);
                    } else {
                        // 再从 description 查找
                        List<String> categories = setuConfig.getCategories();
                        for (String category : categories) {
                            if (category.equalsIgnoreCase(key) || category.toLowerCase().contains(key)) {

                                List<SetuConfig.Api> list = setuConfig.getApis().stream()
                                        .filter(api -> api.getCategory().equalsIgnoreCase(category) && !api.getR18())
                                        .toList();
                                if (!list.isEmpty()) {
                                    selectedApi = RandomUtil.randomEle(list);
                                }
                                break;
                            }
                        }
                    }

                    if (selectedApi == null) {
                        bot.sendMsg(event, "未找到匹配的分类或描述：" + key, false);
                        return;
                    }
                }
            }
            // --------------------------
            // 无参数 → 全局随机
            // --------------------------
            else {
                List<SetuConfig.Api> apis = setuConfig.getApis().stream().filter(e -> !e.getR18()).toList();
                if (apis.isEmpty()) {
                    bot.sendMsg(event, "未配置任何图片 API", false);
                    return;
                }
                selectedApi = apis.get((int) (Math.random() * apis.size()));
            }

            // --------------------------
            // 发送图片
            // --------------------------
            byte[] image = fetchImage(selectedApi);
            bot.sendMsg(event, MsgUtils.builder().img(image).build(), false);

        } catch (Exception e) {
            e.printStackTrace();
            bot.sendMsg(event, "获取图片时出错：" + e.getMessage(), false);
        } finally {
            isFetchingSet.remove(fetchKey);
        }
    }


    @Async
    @PluginFunction(
            group = "瑟瑟功能",
            name = "随机福利图片获取",
            description = "使用 /setur 命令获取随机色图，参数可选ID或分类。无参数则随机抽图",
            commands = {"/setur", "来份色图", "来个色图", "gkd", "来份涩图", "来个涩图", "来份瑟图", "来个瑟图", "来份塞图", "来个塞图"}
    )
    @AnyMessageHandler
    @MessageHandlerFilter(types = MsgTypeEnum.text, cmd = "^(/setur|来(份|个)(色|瑟|涩|塞|)图|gkd)$")
    public void getRandomR18Picture(Bot bot, AnyMessageEvent event, Matcher matcher) {
        Long groupId = event.getGroupId();
        Long userId = event.getUserId();
        boolean isInGroup = groupId != null;
        String fetchKey = groupId != null ? "group_" + groupId : "private_" + event.getUserId();
        if (isFetchingSet.contains(fetchKey)) {
            bot.sendMsg(event, "上一张图片还在加载中，请稍等~", false);
            return;
        }
        isFetchingSet.add(fetchKey);
        String filePath = null;
        String fileName = null;
        try {
            List<SetuConfig.Api> apis = setuConfig.getApis();
            if (apis.isEmpty()) {
                bot.sendMsg(event, "未配置任何图片 API", false);
                return;
            }
            apis = apis.stream().filter(api -> Boolean.TRUE.equals(api.getR18())).toList();
            if (apis.isEmpty()) {
                bot.sendMsg(event, "当前没有配置任何R18图片API~", false);
                return;
            }
            SetuConfig.Api selectedApi = apis.get((int) (Math.random() * apis.size()));
            byte[] image = fetchImage(selectedApi);
            if (image.length == 0) {
                bot.sendMsg(event, "未能获取到图片，请稍后再试~", false);
                return;
            }
            String s = PdfUtil.wrapByteImagesIntoPdf(List.of(image), "setu");
            if (s == null) {
                bot.sendMsg(event, "生成图片时出错，请稍后再试~", false);
                return;
            }
            filePath = s.replace("\\", "/");
            // 从原始路径 's' 中安全地获取文件名
            fileName = Path.of(s).getFileName().toString();
            log.info("Attempting to upload file: path='{}', name='{}'", filePath, fileName);
            // 使用处理过的路径和文件名进行上传
            ActionRaw actionRaw = isInGroup ? bot.uploadGroupFile(event.getGroupId(), filePath, fileName) : bot.uploadPrivateFile(event.getUserId(), filePath, fileName);
            if (actionRaw == null || actionRaw.getRetCode() != 0) {
                log.error("File upload failed. Path: {}, Name: {}. Response: {}", filePath, fileName, actionRaw);
            }
        } catch (Exception e) {
            bot.sendMsg(event, "获取图片时出错：" + e.getMessage(), false);
            log.error("Error during R18 picture fetch/upload", e);
        } finally {
            isFetchingSet.remove(fetchKey);
            if (isInGroup) {
                deleteGroupFile(bot, event,  fileName);
            }
            recycleTempFile(filePath);
        }
    }

    @Async
    protected void deleteGroupFile(Bot bot , AnyMessageEvent anyMessageEvent , String fileName) {
        try {
            TimeUnit.SECONDS.sleep(20);
            Long groupId =  anyMessageEvent.getGroupId();
            ActionData<GroupFilesResp> groupRootFiles = bot.getGroupRootFiles(groupId);
            GroupFilesResp data = groupRootFiles.getData();
            for (GroupFilesResp.Files file : data.getFiles()) {
                if (file.getFileName().equals(fileName)) {
                    bot.deleteGroupFile(groupId,file.getFileId(), file.getBusId());
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


    private byte[] fetchImage(SetuConfig.Api api) throws IOException {
        Request request = new Request.Builder()
                .url(api.getUrl()).build();
        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Request failed");
            }
            if ("json".equalsIgnoreCase(api.getResponseType())) {
                // JSON 响应，解析 URL
                String body = response.body().string();
                JsonNode node = objectMapper.readTree(body);
                String[] paths = api.getJsonPath().split("\\.");
                log.info("Parsing JSON path: {}", Arrays.toString(paths));
                for (String path : paths) {
                    node = node.get(path);
                    if (node.isArray()) {
                        node = node.get(0);
                    }
                    if (node == null) {
                        log.error("Invalid JSON path: {}", api.getJsonPath());
                        log.error("Response body: {}", body);
                        throw new IOException("Invalid JSON path");
                    }
                }
                String imageUrl = node.asText();
                log.info("Fetched image URL: {}", imageUrl);
                try (Response byteResp = httpClient.newCall(new Request.Builder().url(imageUrl).build())
                        .execute()) {
                    return byteResp.body().bytes();
                }
            } else {
                // 直接返回图片
                return response.body().bytes();
            }
        }
    }

    @PluginFunction(
            group = "瑟瑟功能",
            name = "图片资源列表",
            description = "使用 /setu-list 命令查看所有可用的图片接口。",
            commands = {"/setu-list", "/色图列表"}
    )
    @AnyMessageHandler
    @MessageHandlerFilter(cmd = "^/(setu-list|色图列表)$")
    public void listSetuApis(Bot bot, AnyMessageEvent event) {
        List<SetuConfig.Api> apis = setuConfig.getApis();

        if (apis == null || apis.isEmpty()) {
            bot.sendMsg(event, "当前没有配置任何图片接口~", false);
            return;
        }

        bot.sendMsg(event, "正在获取图片资源列表，请稍等...", false);

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<String>> futures = new ArrayList<>();

            // 1. 提交所有任务到线程池，立即返回 Future
            for (SetuConfig.Api api : apis) {
                Future<String> future = executor.submit(() -> {
                    // 这个 lambda 表达式中的代码将在一个独立的虚拟线程中执行
                    MsgUtils builder = MsgUtils.builder();
                    StringBuilder sb = new StringBuilder();
                    sb.append("ID: ").append(api.getId())
                            .append(" | 分类: ").append(api.getCategory() != null ? api.getCategory() : "未知")
                            .append(" | 描述: ").append(api.getDescription() != null ? api.getDescription() : "无描述");
                    //跳过R18
                    if (!api.getR18()) {
                        byte[] bytes;
                        try {
                            // 这是耗时的 I/O 操作
                            bytes = fetchImage(api);
                        } catch (IOException e) {
                            bytes = new byte[0]; // 确保 bytes 不是 null
                            log.error("Error fetching image for API ID {}: {}", api.getId(), e.getMessage());
                            sb.append("\n[图片获取失败: ").append(e.getMessage()).append("]");
                        }
                        // 返回构建好的单条消息字符串
                        return builder.text(sb.toString()).img(bytes).build();
                    } else {
                        return builder.text(sb + "\n[图片被吃掉了]").build();
                    }

                });
                futures.add(future);
            }

            // 2. 收集所有任务的结果
            List<String> msgs = new ArrayList<>(apis.size());
            for (Future<String> future : futures) {
                try {
                    // future.get() 会阻塞，直到该任务完成并返回结果
                    // 由于任务是并行的，这里的等待时间取决于最慢的那个任务
                    msgs.add(future.get());
                } catch (InterruptedException | ExecutionException e) {
                    // 如果任务本身执行出错（例如，内部抛出了未捕获的异常）
                    log.error("A task failed during parallel execution", e);
                    // 添加一条错误提示信息，避免整个流程失败
                    msgs.add(MsgUtils.builder().text("一个任务执行失败: " + e.getMessage()).build());
                }
            }

            // 3. 发送合并转发消息
            List<Map<String, Object>> maps = ShiroUtils.generateForwardMsg(msgs);
            bot.sendForwardMsg(event, maps);

        }
    }


    @PluginFunction(
            group = "瑟瑟功能",
            name = "图片分类列表",
            description = "使用 /setu-desc 命令查看所有可用分类。",
            commands = {"/setu-desc", "/色图分类"}
    )
    @AnyMessageHandler
    @MessageHandlerFilter(cmd = "^/(setu-desc|色图分类)$")
    public void listSetuDesc(Bot bot, AnyMessageEvent event) {
        List<String> categories = setuConfig.getCategories();
        if (categories == null || categories.isEmpty()) {
            bot.sendMsg(event, "当前没有配置任何图片分类~", false);
            return;
        }
        StringBuilder sb = new StringBuilder("可用图片分类列表：\n");
        for (String category : categories) {
            sb.append("- ").append(category).append("\n");
        }
        bot.sendMsg(event, sb.toString(), false);
    }


}