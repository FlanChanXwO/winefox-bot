package com.github.winefoxbot.plugins.pixiv;

import com.github.winefoxbot.core.annotation.Plugin;
import com.github.winefoxbot.core.annotation.PluginFunction;
import com.github.winefoxbot.plugins.pixiv.model.dto.common.PixivArtworkInfo;
import com.github.winefoxbot.plugins.pixiv.model.dto.search.PixivSearchParams;
import com.github.winefoxbot.plugins.pixiv.model.dto.search.PixivSearchResult;
import com.github.winefoxbot.core.model.enums.Permission;
import com.github.winefoxbot.core.model.enums.SessionType;
import com.github.winefoxbot.plugins.pixiv.service.PixivArtworkService;
import com.github.winefoxbot.plugins.pixiv.service.PixivSearchService;
import com.github.winefoxbot.plugins.pixiv.service.PixivService;
import com.github.winefoxbot.core.service.shiro.ShiroSessionStateService;
import com.mikuac.shiro.annotation.AnyMessageHandler;
import com.mikuac.shiro.annotation.MessageHandlerFilter;
import com.mikuac.shiro.annotation.common.Order;
import com.mikuac.shiro.annotation.common.Shiro;
import com.mikuac.shiro.common.utils.MsgUtils;
import com.mikuac.shiro.core.Bot;
import com.mikuac.shiro.dto.event.message.AnyMessageEvent;
import com.mikuac.shiro.enums.MsgTypeEnum;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static com.github.winefoxbot.core.config.app.WineFoxBotConfig.*;
import static com.github.winefoxbot.core.utils.BotUtils.checkStrictSessionIdType;
import static com.mikuac.shiro.core.BotPlugin.MESSAGE_BLOCK;
import static com.mikuac.shiro.core.BotPlugin.MESSAGE_IGNORE;

/**
 * @author FlanChan (badapple495@outlook.com)
 * @since 2026-01-01-16:29
 */
@Plugin(name = "Pixiv",
        description = "提供 Pixiv 图片获取与排行榜订阅等功能",
        permission = Permission.USER,
        iconPath = "icon/pixiv.png",
        order = 13
)
@Shiro
@Component
@RequiredArgsConstructor
@Slf4j
public class PixivSearchPlugin {
    // 核心服务
    private final PixivSearchService pixivSearchService;
    private final PixivService pixivService;
    private final ShiroSessionStateService sessionStateService;
    // 重构后引入的统一发送服务
    private final PixivArtworkService artworkService;

    // 会话管理
    private final Map<String, LastSearchResult> lastSearchResultMap = new ConcurrentHashMap<>();
    private final Map<String, ScheduledFuture<?>> sessionTimeoutTasks = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    // 用户并发请求控制
    private final ConcurrentHashMap<Long, AtomicInteger> userRequestCounts = new ConcurrentHashMap<>();
    private static final int MAX_CONCURRENT_REQUESTS_PER_USER = 3;

    // 常量定义
    private static final Pattern NUMBER_SELECTION_PATTERN = Pattern.compile("^[\\d,，\\s]+$");
    private static final long SESSION_TIMEOUT_SECONDS = 60 * 5;

    private static class LastSearchResult {
        PixivSearchParams params;
        PixivSearchResult result;
        AnyMessageEvent event;
        Long initiatorUserId;

        LastSearchResult(PixivSearchParams params, PixivSearchResult result, AnyMessageEvent event) {
            this.params = params;
            this.result = result;
            this.event = event;
            this.initiatorUserId = event.getUserId();
        }
    }

    @Async
    @PluginFunction(
            name = "Pixiv搜索",
            permission = Permission.USER,
            autoGenerateHelp = false,
            description = "在Pixiv上搜索插画作品。命令格式：" + COMMAND_PREFIX + "pixiv搜索 <标签1> <标签2> ... [-p<页码>] [-r]" + COMMAND_SUFFIX + "。其中 -p 用于指定页码，-r 用于开启R18搜索。",
            commands = {
                    COMMAND_PREFIX + "pixiv搜索 <标签1> <标签2> ... [-p<页码>] [-r]" + COMMAND_SUFFIX,
                    COMMAND_PREFIX + "P站搜索 <标签1> <标签2> ... [-p<页码>] [-r]" + COMMAND_SUFFIX,
                    COMMAND_PREFIX + "Pixiv搜索 <标签1> <标签2> ... [-p<页码>] [-r]" + COMMAND_SUFFIX,
                    COMMAND_PREFIX + "p站搜索 <标签1> <标签2> ... [-p<页码>] [-r]" + COMMAND_SUFFIX,
            })
    @AnyMessageHandler
    @MessageHandlerFilter(types = MsgTypeEnum.text, cmd = COMMAND_PREFIX_REGEX + "(?:p|P)(?:ixiv|站)搜索\\s+(.+?)(?=\\s+-|$)\\s*(.*)" + COMMAND_SUFFIX_REGEX)
    public void handlePixivSearch(Bot bot, AnyMessageEvent event, Matcher matcher) {
        PixivSearchParams params = new PixivSearchParams();
        params.setPageNo(1);
        params.setR18(false);
        String keywords = matcher.group(1).trim();
        if (keywords.isEmpty()) {
            bot.sendMsg(event, "请输入至少一个搜索标签！", false);
            return;
        }
        List<String> tags = new ArrayList<>(Arrays.asList(keywords.split("\\s+")));
        params.setTags(tags);
        String arguments = matcher.group(2).trim();
        if (!arguments.isEmpty()) {
            String[] args = arguments.split("\\s+");
            for (String arg : args) {
                if ("-r".equalsIgnoreCase(arg)) {
                    params.setR18(true);
                    continue;
                }
                if (arg.toLowerCase().startsWith("-p")) {
                    String pageStr = arg.substring(2);
                    if (!pageStr.isEmpty()) {
                        try {
                            int pageNo = Integer.parseInt(pageStr);
                            if (pageNo > 0) {
                                params.setPageNo(pageNo);
                            } else {
                                bot.sendMsg(event, "页码必须是大于0的整数哦。", false);
                                return;
                            }
                        } catch (NumberFormatException e) {
                            log.warn("无效的页码参数: {}", arg);
                            bot.sendMsg(event, "页码参数格式不正确，应为 -p<数字>，例如 -p2。", false);
                            return;
                        }
                    }
                }
            }
        }
        executeSearch(bot, event, params);
    }

    @Async
    @AnyMessageHandler
    @Order(1)
    @MessageHandlerFilter(types = MsgTypeEnum.text)
    public Future<Integer> handleSearchResultInteraction(Bot bot, AnyMessageEvent event) {
        String sessionId = sessionStateService.getSessionKey(event);
        LastSearchResult lastSearch = lastSearchResultMap.get(sessionId);
        String message = event.getMessage().trim();

        if (lastSearch == null || !Objects.equals(lastSearch.initiatorUserId, event.getUserId())) {
            return CompletableFuture.completedFuture(MESSAGE_IGNORE);
        }

        if (message.matches(COMMAND_PREFIX_REGEX + ".+" + COMMAND_SUFFIX_REGEX)) {
            clearSession(sessionId);
            sessionStateService.exitCommandMode(sessionId);
            bot.sendMsg(event, "已退出当前Pixiv搜索会话，开始处理新命令。", false);
            return CompletableFuture.completedFuture(MESSAGE_IGNORE);
        }

        lastSearch.event = event;

        boolean isExit = "退出".equals(message) || "exit".equalsIgnoreCase(message);
        boolean isPaging = "下一页".equals(message) || "上一页".equals(message);
        boolean isSelection = NUMBER_SELECTION_PATTERN.matcher(message).matches();

        if (isExit) {
            clearSession(sessionId);
            sessionStateService.exitCommandMode(sessionId);
            String tipMessage = "已退出当前搜索会话";
            SessionType sessionType = checkStrictSessionIdType(sessionId);
            String quitMessage = switch (sessionType) {
                case GROUP -> MsgUtils.builder().at(event.getUserId()).text(" " + tipMessage).build();
                case PRIVATE -> MsgUtils.builder().text(tipMessage).build();
            };
            bot.sendMsg(event, quitMessage, false);
            return CompletableFuture.completedFuture(MESSAGE_BLOCK);
        }

        if (isPaging) {
            int currentPage = lastSearch.result.getCurrentPage();
            int totalPages = lastSearch.result.getTotalPages();
            if ("下一页".equals(message)) {
                if (currentPage >= totalPages) {
                    bot.sendMsg(event, "已经是最后一页啦！", false);
                } else {
                    lastSearch.params.setPageNo(currentPage + 1);
                    executeSearch(bot, event, lastSearch.params);
                }
            } else { // 上一页
                if (currentPage <= 1) {
                    bot.sendMsg(event, "已经是第一页啦！", false);
                } else {
                    lastSearch.params.setPageNo(currentPage - 1);
                    executeSearch(bot, event, lastSearch.params);
                }
            }
        } else if (isSelection) {
            List<Integer> selectedIndexes = Arrays.stream(message.split("[,，\\s]+"))
                    .filter(s -> !s.isEmpty()).map(Integer::parseInt).collect(Collectors.toList());
            List<PixivSearchResult.ArtworkData> artworks = lastSearch.result.getArtworks();
            if (artworks == null || artworks.isEmpty()) {
                bot.sendMsg(event, "当前搜索结果中没有作品数据，无法选择。", false);
            } else {
                bot.sendMsg(event, String.format("收到！准备发送你选择的 %d 个作品...", selectedIndexes.size()), false);
                for (int index : selectedIndexes) {
                    if (index > 0 && index <= artworks.size()) {
                        String pid = artworks.get(index - 1).getPid();
                        log.info("用户 {} 选择了作品 PID: {}", event.getUserId(), pid);
                        processArtworkRequest(bot, event, pid);
                    } else {
                        bot.sendMsg(event, String.format("序号 %d 超出范围啦，请输入 1 到 %d 之间的数字。", index, artworks.size()), false);
                    }
                }
            }
        } else {
            bot.sendMsg(event, "未知指令。请发送【序号】、【上一页】/【下一页】或【退出】。", false);
        }

        resetSessionTimeout(bot, sessionId);
        return CompletableFuture.completedFuture(MESSAGE_BLOCK);
    }

    /**
     * 处理单个作品的获取请求，包含并发检查。
     */
    private void processArtworkRequest(Bot bot, AnyMessageEvent event, String pid) {
        Long userId = event.getUserId();
        AtomicInteger count = userRequestCounts.computeIfAbsent(userId, k -> new AtomicInteger(0));

        if (count.get() >= MAX_CONCURRENT_REQUESTS_PER_USER) {
            String tipMessage = String.format("你当前有 %d 个图片正在获取中，请稍后再试哦。", count.get());
            String message = (event.getGroupId() != null)
                    ? MsgUtils.builder().at(userId).text(" " + tipMessage).build()
                    : tipMessage;
            bot.sendMsg(event, message, false);
            return;
        }

        count.incrementAndGet();
        sendArtworkByPidAsync(bot, event, pid);
    }

    /**
     * 异步获取作品信息并调用统一的发送服务。
     * 这是重构的核心，内部逻辑被 `artworkSenderService` 替代。
     */
    @Async("taskExecutor")
    public void sendArtworkByPidAsync(Bot bot, AnyMessageEvent event, String pid) {
        try {
            // 1. 获取作品详细信息
            PixivArtworkInfo pixivArtworkInfo = pixivService.getPixivArtworkInfo(pid);

            // 2. 异步下载图片文件
            List<File> files = pixivService.fetchImages(pid).join();

            // 3. 调用统一的发送服务
            String additionalText = "\n可以继续发送【序号】获取其他作品，或发送【退出】结束本次搜索。";
            artworkService.sendArtwork(bot, event, pixivArtworkInfo, files, additionalText);

        } catch (IOException e) {
            log.error("获取 Pixiv 作品信息时发生IO异常 pid={}", pid, e);
            bot.sendMsg(event, MsgUtils.builder().reply(event.getMessageId()).text("获取 Pixiv 作品信息失败，可能是网络问题，请重试。").build(), false);
        } catch (Exception e) {
            log.error("处理 Pixiv 图片失败 pid={}", pid, e);
            bot.sendMsg(event, MsgUtils.builder().reply(event.getMessageId()).text("处理 Pixiv 图片时发生未知错误：" + e.getMessage()).build(), false);
        } finally {
            // 任务结束，减少并发计数
            userRequestCounts.get(event.getUserId()).decrementAndGet();
            log.info("PID: {} 获取任务完成，用户 {} 的并发数减一", pid, event.getUserId());
        }
    }

    private void executeSearch(Bot bot, AnyMessageEvent event, PixivSearchParams params) {
        log.info("开始Pixiv搜索，关键词: {}, 参数: pageNo={}, isR18={}", params.getTags(), params.getPageNo(), params.isR18());
        bot.sendMsg(event, "正在搜索，请稍候...", false);
        try {
            PixivSearchResult result = pixivSearchService.search(params);
            String sessionId = sessionStateService.getSessionKey(event);
            if (result != null && result.getScreenshot() != null && result.getTotalArtworks() > 0) {
                sessionStateService.enterCommandMode(sessionId);
                lastSearchResultMap.put(sessionId, new LastSearchResult(params, result, event));
                resetSessionTimeout(bot, sessionId);
                String tagsString = String.join(" ", params.getTags());
                String r18Flag = params.isR18() ? " -r" : "";
                String previousCommand = String.format("pixiv搜索 %s%s -p", tagsString, r18Flag);
                MsgUtils msg = MsgUtils.builder()
                        .text(String.format("为你找到了关于 [%s] 的以下结果：\n", String.join(", ", params.getTags())))
                        .text(String.format("共 %d 个作品，当前在第 %d/%d 页。\n",
                                result.getTotalArtworks(), result.getCurrentPage(), result.getTotalPages()))
                        .img(result.getScreenshot())
                        .text(String.format("\n你可以发送【上一页】/【下一页】翻页，或【%s<页码>】跳转。\n", previousCommand))
                        .text(String.format("发送图片上的【序号】可获取原图。发送【退出】结束会话。\n(会话将在%d秒后无操作自动结束)", SESSION_TIMEOUT_SECONDS));
                bot.sendMsg(event, msg.build(), false);
            } else {
                clearSession(sessionId);
                sessionStateService.exitCommandMode(sessionId);
                String noResultMessage = String.format("抱歉，没有找到关于 [%s] 的结果呢。", String.join(" ", params.getTags()));
                if (params.isR18()) noResultMessage += " (已在R18分类下搜索)";
                if (params.getPageNo() > 1) noResultMessage += String.format(" (在第%d页)", params.getPageNo());
                bot.sendMsg(event, noResultMessage, false);
            }
        } catch (Exception e) {
            log.error("Pixiv搜索时发生异常", e);
            bot.sendMsg(event, "搜索过程中发生内部错误，请联系管理员。", false);
        }
    }

    private void resetSessionTimeout(Bot bot, String sessionId) {
        ScheduledFuture<?> oldTask = sessionTimeoutTasks.remove(sessionId);
        if (oldTask != null) {
            oldTask.cancel(false);
        }
        LastSearchResult currentSearch = lastSearchResultMap.get(sessionId);
        if (currentSearch == null) {
            log.warn("尝试重置超时任务时，会话 [{}] 已被清除，操作终止。", sessionId);
            sessionStateService.exitCommandMode(sessionId);
            return;
        }
        ScheduledFuture<?> newTask = scheduler.schedule(() -> {
            LastSearchResult removedSearch = lastSearchResultMap.remove(sessionId);
            if (removedSearch != null) {
                sessionStateService.exitCommandMode(sessionId);
                sessionTimeoutTasks.remove(sessionId);
                String tipMessage = "Pixiv搜索会话已超时，请重新发起搜索。";
                log.info("Pixiv搜索会话 [{}] 因超时已自动结束。", sessionId);
                String message = (removedSearch.event.getGroupId() != null)
                        ? MsgUtils.builder().at(removedSearch.initiatorUserId).text(" " + tipMessage).build()
                        : tipMessage;
                bot.sendMsg(removedSearch.event, message, false);
            }
        }, SESSION_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        sessionTimeoutTasks.put(sessionId, newTask);
    }

    private void clearSession(String sessionId) {
        ScheduledFuture<?> task = sessionTimeoutTasks.remove(sessionId);
        if (task != null) {
            task.cancel(false);
        }
        lastSearchResultMap.remove(sessionId);
    }
}
