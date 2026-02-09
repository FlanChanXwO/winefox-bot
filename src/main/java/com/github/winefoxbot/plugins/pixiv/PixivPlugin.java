package com.github.winefoxbot.plugins.pixiv;

import com.github.winefoxbot.core.annotation.common.Limit;
import com.github.winefoxbot.core.annotation.plugin.Plugin;
import com.github.winefoxbot.core.annotation.plugin.PluginFunction;
import com.github.winefoxbot.core.config.plugin.BasePluginConfig;
import com.github.winefoxbot.core.context.BotContext;
import com.github.winefoxbot.core.exception.bot.BotException;
import com.github.winefoxbot.core.model.entity.ShiroScheduleTask;
import com.github.winefoxbot.core.model.enums.common.MessageType;
import com.github.winefoxbot.core.model.enums.common.Permission;
import com.github.winefoxbot.core.model.enums.common.PushTargetType;
import com.github.winefoxbot.core.service.schedule.ShiroScheduleTaskService;
import com.github.winefoxbot.core.service.schedule.handler.BotJobHandler;
import com.github.winefoxbot.core.service.shiro.ShiroSessionStateService;
import com.github.winefoxbot.core.utils.CronFormatter;
import com.github.winefoxbot.core.utils.FileUtil;
import com.github.winefoxbot.core.utils.NumberParserUtil;
import com.github.winefoxbot.plugins.pixiv.config.PixivPluginConfig;
import com.github.winefoxbot.plugins.pixiv.job.PixivRankDailyJob;
import com.github.winefoxbot.plugins.pixiv.job.PixivRankMonthlyJob;
import com.github.winefoxbot.plugins.pixiv.job.PixivRankWeeklyJob;
import com.github.winefoxbot.plugins.pixiv.model.dto.common.PixivArtworkInfo;
import com.github.winefoxbot.plugins.pixiv.model.dto.search.PixivSearchParams;
import com.github.winefoxbot.plugins.pixiv.model.dto.search.PixivSearchResult;
import com.github.winefoxbot.plugins.pixiv.model.entity.PixivBookmark;
import com.github.winefoxbot.plugins.pixiv.model.enums.PixivRankPushMode;
import com.github.winefoxbot.plugins.pixiv.service.*;
import com.github.winefoxbot.plugins.pixiv.utils.PixivUtils;
import com.mikuac.shiro.annotation.AnyMessageHandler;
import com.mikuac.shiro.annotation.MessageHandlerFilter;
import com.mikuac.shiro.annotation.common.Order;
import com.mikuac.shiro.common.utils.MsgUtils;
import com.mikuac.shiro.common.utils.ShiroUtils;
import com.mikuac.shiro.core.Bot;
import com.mikuac.shiro.dto.event.message.AnyMessageEvent;
import com.mikuac.shiro.enums.MsgTypeEnum;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.tomcat.util.buf.StringUtils;
import org.jobrunr.scheduling.cron.Cron;
import org.springframework.scheduling.annotation.Async;

import javax.net.ssl.SSLHandshakeException;
import java.io.File;
import java.io.IOException;
import java.time.DayOfWeek;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static com.github.winefoxbot.core.config.app.WineFoxBotConfig.*;
import static com.mikuac.shiro.core.BotPlugin.MESSAGE_BLOCK;
import static com.mikuac.shiro.core.BotPlugin.MESSAGE_IGNORE;


/**
 * @author FlanChan
 */
@Plugin(name = "Pixiv",
        permission = Permission.USER,
        iconPath = "icon/pixiv.png",
        description = "提供Pixiv图片获取、排行榜订阅、搜索等功能。",
        order = 13,
        config = PixivPluginConfig.class
)
@Slf4j
@RequiredArgsConstructor
public class PixivPlugin {

    private final PixivService pixivService;
    private final PixivRankService pixivRankService;
    private final PixivArtworkService artworkService;
    // 注入通用调度服务
    private final ShiroScheduleTaskService scheduleService;

    //region Pixiv基础功能

    @PluginFunction(name = "查看P站排行订阅状态", description = "查看当前群聊的P站排行订阅状态。", commands = {COMMAND_PREFIX + "查看P站排行订阅" + COMMAND_SUFFIX, COMMAND_PREFIX + "p站订阅状态" + COMMAND_SUFFIX})
    @AnyMessageHandler
    @MessageHandlerFilter(types = MsgTypeEnum.text, cmd = COMMAND_PREFIX_REGEX + "(查看P站排行订阅|p站订阅状态)" + COMMAND_SUFFIX_REGEX)
    public void checkRankPushSubscription(Bot bot, AnyMessageEvent event) {
        if (event.getGroupId() == null) {
            bot.sendMsg(event, "此功能仅限群聊使用。", true);
            return;
        }
        Long groupId = event.getGroupId();

        // 获取该群所有的调度任务
        List<ShiroScheduleTask> allTasks = scheduleService.listTaskConfigs(bot.getSelfId(), PushTargetType.GROUP, groupId);
        String dkey = scheduleService.resolveTaskKey(PixivRankDailyJob.class);
        String wkey = scheduleService.resolveTaskKey(PixivRankWeeklyJob.class);
        String mkey = scheduleService.resolveTaskKey(PixivRankMonthlyJob.class);

        // 筛选出 Pixiv 相关的任务
        List<ShiroScheduleTask> pixivTasks = allTasks.stream()
                .filter(t -> t.getTaskType().equals(dkey)
                        || t.getTaskType().equals(wkey)
                        || t.getTaskType().equals(mkey))
                .toList();

        if (pixivTasks.isEmpty()) {
            bot.sendMsg(event, "本群尚未订阅任何P站排行推送。\n发送 `/订阅P站排行` 查看帮助。", true);
            return;
        }

        StringBuilder reply = new StringBuilder("本群P站排行订阅状态如下：\n");
        for (ShiroScheduleTask schedule : pixivTasks) {
            String readableTime = CronFormatter.parseCronToDescription(schedule.getCronExpression());
            // taskParam 存储的是 mode (daily/weekly/monthly)
            String modeStr = String.valueOf(schedule.getTaskParam());
            String desc = switch (modeStr) {
                case "daily" -> "P站每日排行榜";
                case "weekly" -> "P站每周排行榜";
                case "monthly" -> "P站每月排行榜";
                default -> "P站未知排行(" + modeStr + ")";
            };

            reply.append(String.format("【%s】推送时间：%s\n", desc, readableTime));
        }
        bot.sendMsg(event, reply.toString().trim(), true);
    }

    @PluginFunction( name = "订阅P站排行",
            permission = Permission.ADMIN,
            description = "订阅P站排行榜推送。用法: " + COMMAND_PREFIX + "订阅P站排行榜 [类型] [时间]" + COMMAND_SUFFIX + "。类型支持\"每日\"，\"每周\"，\"每月\", 例如: /订阅P站排行榜 每日 09:30", commands = {COMMAND_PREFIX + "订阅P站排行榜 每日 09:30" + COMMAND_SUFFIX, COMMAND_PREFIX + "订阅P站排行榜 每周 10:00" + COMMAND_SUFFIX, COMMAND_PREFIX + "订阅P站排行榜 每月 12:00" + COMMAND_SUFFIX})
    @AnyMessageHandler
    @MessageHandlerFilter(types = MsgTypeEnum.text, cmd = COMMAND_PREFIX_REGEX + "订阅P站排行榜(?:\\s+(每日|每周|每月))?(?:\\s+([0-2][0-9]):([0-5][0-9]))?" + COMMAND_SUFFIX_REGEX)
    public void subscribeRankPush(Bot bot, AnyMessageEvent event, Matcher matcher) {
        if (event.getGroupId() == null) {
            bot.sendMsg(event, "此功能仅限群聊使用。", true);
            return;
        }
        Long groupId = event.getGroupId();
        String rankType = matcher.group(1);
        String hourStr = matcher.group(2);
        String minuteStr = matcher.group(3);

        if (rankType == null || hourStr == null) {
            String help = """
                    指令格式错误！
                    用法: /订阅P站排行 [类型] [时间 HH:mm]
                    支持的类型: 每日, 每周, 每月
                    示例:
                    /订阅P站排行 每日 09:30
                    /订阅P站排行 每周 10:00 (默认为周五)
                    /订阅P站排行 每月 12:00 (默认为月底)""";
            bot.sendMsg(event, help, true);
            return;
        }

        int hour = Integer.parseInt(hourStr);
        int minute = Integer.parseInt(minuteStr);
        String cronExpression = null;
        String description = null;
        PixivRankPushMode mode = null;
        switch (rankType) {
            case "每日":
                cronExpression = Cron.daily(hour, minute);
                description = "P站每日排行榜";
                mode = PixivRankPushMode.DALLY;
                break;
            case "每周":
                cronExpression = Cron.weekly(DayOfWeek.of(5), hour, minute);
                description = "P站每周排行榜";
                mode = PixivRankPushMode.WEEKLY;
                break;
            case "每月":
                cronExpression = Cron.lastDayOfTheMonth(hour, minute);
                description = "P站每月排行榜";
                mode = PixivRankPushMode.MONTHLY;
                break;
        }

        Class<? extends BotJobHandler<String, BasePluginConfig>> scheduledJobClass = switch (mode) {
            case DALLY -> PixivRankDailyJob.class;
            case WEEKLY -> PixivRankWeeklyJob.class;
            case MONTHLY -> PixivRankMonthlyJob.class;
        };

        // 使用新版调度接口，传入 mode.getValue() 作为参数
        scheduleService.scheduleHandler(
                bot.getSelfId(),
                PushTargetType.GROUP,
                groupId,
                cronExpression,
                scheduledJobClass,
                mode.getValue() // 参数：daily, weekly, monthly
        );

        String readableTime = CronFormatter.parseCronToDescription(cronExpression);
        bot.sendMsg(event, String.format("成功订阅/更新【%s】！\n推送时间设置为：%s", description, readableTime), true);
    }


    @PluginFunction( name = "取消P站排行榜订阅",
            permission = Permission.ADMIN,
            description = "取消订阅P站排行榜。用法: /取消P站排行榜 [类型]", commands = {COMMAND_PREFIX + "取消P站排行榜 每日" + COMMAND_SUFFIX,
            COMMAND_PREFIX + "取消P站排行榜 每周" + COMMAND_SUFFIX, COMMAND_PREFIX + "取消P站排行榜 每月" + COMMAND_SUFFIX})
    @AnyMessageHandler
    @MessageHandlerFilter(types = MsgTypeEnum.text, cmd = COMMAND_PREFIX_REGEX + "取消P站排行榜\\s+(每日|每周|每月)" + COMMAND_SUFFIX_REGEX)
    public void unsubscribeRankPush(Bot bot, AnyMessageEvent event, Matcher matcher) {
        if (event.getGroupId() == null) {
            bot.sendMsg(event, "此功能仅限群聊使用。", true);
            return;
        }
        Long groupId = event.getGroupId();
        String rankType = matcher.group(1);
        PixivRankPushMode mode = switch (rankType) {
            case "每日" -> PixivRankPushMode.DALLY;
            case "每周" -> PixivRankPushMode.WEEKLY;
            case "每月" -> PixivRankPushMode.MONTHLY;
            default -> null;
        };
        if (mode == null) {
            bot.sendMsg(event, "无效的排行榜类型！请使用 每日、每周 或 每月。", true);
            return;
        }

        // 查找并删除特定参数的任务
        List<ShiroScheduleTask> tasks = scheduleService.listTaskConfigs(bot.getSelfId(), PushTargetType.GROUP, groupId);

        String jobkey = switch (mode) {
            case DALLY -> scheduleService.resolveTaskKey(PixivRankDailyJob.class);
            case WEEKLY -> scheduleService.resolveTaskKey(PixivRankWeeklyJob.class);
            case MONTHLY -> scheduleService.resolveTaskKey(PixivRankMonthlyJob.class);
        };

        ShiroScheduleTask targetTask = tasks.stream()
                .filter(t -> jobkey.equals(t.getTaskType())) // 匹配 Key
                .findFirst()
                .orElse(null);

        if (targetTask != null) {
            // 通过 ID 删除 (ShiroScheduleTaskService 继承了 IService，所以有 removeById)
            scheduleService.removeById(targetTask.getId());

            String description = switch (mode) {
                case DALLY -> "P站每日排行榜";
                case WEEKLY -> "P站每周排行榜";
                case MONTHLY -> "P站每月排行榜";
            };
            bot.sendMsg(event, String.format("已成功取消【%s】的订阅。", description), true);
        } else {
            bot.sendMsg(event, "取消失败，可能本群未订阅该类型的排行榜。", true);
        }
    }


    @Async
    @PluginFunction( name = "Pixiv 图片获取", description = "使用 " + COMMAND_PREFIX + "p <PID 或 URL>" + COMMAND_SUFFIX + " 命令获取 Pixiv 作品图片。", commands = {COMMAND_PREFIX + "p <PID 或 URL>" + COMMAND_SUFFIX})
    @AnyMessageHandler
    @MessageHandlerFilter(types = MsgTypeEnum.text, cmd = COMMAND_PREFIX_REGEX + "(p|P|pixiv)(?:\\s+(\\S+))?" + COMMAND_SUFFIX_REGEX)
    public void getPixivPic(Bot bot, AnyMessageEvent event, Matcher matcher) {
        String arg = matcher.group(2);
        Integer messageId = event.getMessageId();
        if (arg == null || (!PixivUtils.isPixivArtworkUrl(arg) && !arg.matches("\\d+"))) {
            return; // 忽略无效命令
        }
        String pid = PixivUtils.extractPID(arg);
        try {
            if (pid == null || !pixivService.isValidPixivPID(pid)) {
                bot.sendMsg(event, MsgUtils.builder().reply(messageId).text("无效的 Pixiv PID 或 URL！").build(), false);
                return;
            }
            bot.sendMsg(event, MsgUtils.builder().reply(messageId).text("正在处理 Pixiv 图片，请稍候...").build(), false);
            PixivArtworkInfo pixivArtworkInfo = pixivService.getPixivArtworkInfo(pid);
            List<File> files = pixivService.fetchImages(pid).join();
            // 调用统一的发送服务
            artworkService.sendArtwork(pixivArtworkInfo, files, null);
        } catch (SSLHandshakeException e) {
            log.error("Pixiv SSL 握手失败", e);
            bot.sendMsg(event, MsgUtils.builder().reply(messageId).text("网络问题导致图片获取失败，请重试").build(), false);
        } catch (Exception e) {
            log.error("处理 Pixiv 图片失败 pid={}", pid, e);
            bot.sendMsg(event, MsgUtils.builder().reply(messageId).text("处理 Pixiv 图片失败：" + e.getMessage()).build(), false);
        }
    }

    @Async
    @PluginFunction(name = "Pixiv 排行榜获取", description = "获取 Pixiv 排行榜前6名插画作品。",
            commands = {COMMAND_PREFIX + "p站今日排行榜" + COMMAND_SUFFIX,
                    COMMAND_PREFIX + "p站本周排行榜" + COMMAND_SUFFIX,
                    COMMAND_PREFIX + "p站本月排行榜" + COMMAND_SUFFIX,
                    COMMAND_PREFIX + "prd" + COMMAND_SUFFIX,
                    COMMAND_PREFIX + "prw" + COMMAND_SUFFIX,
                    COMMAND_PREFIX + "prm" + COMMAND_SUFFIX})
    @AnyMessageHandler
    @MessageHandlerFilter(types = MsgTypeEnum.text, cmd = COMMAND_PREFIX_REGEX + "((p|P)站(本|今)(日|周|月)排行榜|pr(d|w|m))(?:\\s+(\\S+))?" + COMMAND_SUFFIX_REGEX)
    public void getPixivRankByType(Bot bot, AnyMessageEvent event, Matcher matcher) throws Exception {
        bot.sendMsg(event, "正在获取 Pixiv 排行榜，请稍候...", false);

        String rankType = matcher.group(4); // 日, 周, 月
        if (rankType == null) {
            String shortType = matcher.group(5); // d, w, m
            if (shortType != null) {
                switch (shortType) {
                    case "d" -> rankType = "日";
                    case "w" -> rankType = "周";
                    case "m" -> rankType = "月";
                }
            }
        }

        String params = matcher.group(6);
        PixivRankPushMode mode;

        if (rankType == null) {
            bot.sendMsg(event, "无法识别的命令格式！", true);
            return;
        }

        switch (rankType) {
            case "日" -> mode = PixivRankPushMode.DALLY;
            case "周" -> mode = PixivRankPushMode.WEEKLY;
            case "月" -> mode = PixivRankPushMode.MONTHLY;
            default -> {
                bot.sendMsg(event, "无效的排行榜类型！请使用 日、周 或 月。", true);
                return;
            }
        }

        PixivRankService.Content content = params != null ? PixivRankService.Content.valueOf(params.toUpperCase()) : PixivRankService.Content.ILLUST;
        getPixivRank(bot, event, matcher, mode, content);
    }


    private void getPixivRank(Bot bot, AnyMessageEvent event, Matcher matcher, PixivRankPushMode mode, PixivRankService.Content content) throws Exception {
        String params = matcher.group(2);
        BotContext.callWithContext(BotContext.CURRENT_BOT.get(),BotContext.CURRENT_MESSAGE_EVENT.get(),() -> {
            try {
                List<String> msgList = new ArrayList<>();
                List<String> rankIds = pixivRankService.getRank(mode, content, false);
                List<List<File>> filesList = new ArrayList<>();
                for (String rankId : rankIds) {
                    List<File> files = pixivService.fetchImages(rankId).join();
                    if (files.isEmpty()) {
                        continue;
                    }
                    PixivArtworkInfo pixivArtworkInfo = pixivService.getPixivArtworkInfo(rankId);
                    MsgUtils builder = MsgUtils.builder();
                    builder.text(String.format("""
                                作品标题：%s (%s)
                                作者：%s (%s)
                                描述信息：%s
                                作品链接：https://www.pixiv.net/artworks/%s
                                标签：%s
                                """, pixivArtworkInfo.getTitle(), pixivArtworkInfo.getPid(),
                            pixivArtworkInfo.getUserName(), pixivArtworkInfo.getUid(),
                            pixivArtworkInfo.getDescription(),
                            pixivArtworkInfo.getPid(),
                            StringUtils.join(pixivArtworkInfo.getTags(), ',')));
                    for (File file : files) {
                        String filePath = FileUtil.getFileUrlPrefix() + file.getAbsolutePath();
                        builder.img(filePath);
                    }
                    filesList.add(files);
                    String msg = builder.build();
                    msgList.add(msg);
                }
                if (msgList.isEmpty()) {
                    bot.sendMsg(event, "未能获取到排行榜数据", false);
                    return null;
                }
                List<Map<String, Object>> forwardMsg = ShiroUtils.generateForwardMsg(bot, msgList);
                bot.sendForwardMsg(event, forwardMsg);
                return null;
            } catch (SSLHandshakeException e) {
                log.error("Pixiv SSL 握手失败，可能是 Pixiv 证书发生变更导致，请检查！", e);
                bot.sendMsg(event, "因为网络问题，图片获取失败，请重试", false);
            } catch (IllegalArgumentException e) {
                log.error("无效的排行榜参数: {}", params, e);
                bot.sendMsg(event, "无效的排行榜参数: " + params, false);
            } catch (Exception e) {
                log.error("处理 Pixiv 图片失败", e);
                bot.sendMsg(event, "处理 Pixiv 图片失败：" + e.getMessage(), false);
            }
            return null;
        });

    }

    //endregion

    //region P站搜素
    // 核心服务
    private final PixivSearchService pixivSearchService;
    private final ShiroSessionStateService sessionStateService;

    // 会话管理
    private final Map<String, LastSearchResult> lastSearchResultMap = new ConcurrentHashMap<>();
    private final Map<String, ScheduledFuture<?>> sessionTimeoutTasks = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler;

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
            MessageType messageType = MessageType.fromValue(event.getMessageType());
            String quitMessage = switch (messageType) {
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
            artworkService.sendArtwork(pixivArtworkInfo, files, additionalText);

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
    //endregion

    //region 收藏夹

    private final PixivArtworkService pixivArtworkService;
    private final PixivBookmarkService pixivBookmarkService;

    @Async
    @PluginFunction(name = "同步 Pixiv 收藏夹",
            description = "手动同步 Pixiv 收藏夹中的作品",
            permission = Permission.SUPERADMIN,
            autoGenerateHelp = false,
            commands = { "/同步P站收藏" ,"/同步p站收藏"}
    )
    @AnyMessageHandler
    @MessageHandlerFilter(types = MsgTypeEnum.text, cmd = COMMAND_PREFIX_REGEX + "同步((p|P)(ixiv|站))收藏" + COMMAND_SUFFIX_REGEX)
    public void syncPixivBookmarks(Bot bot, AnyMessageEvent event) {
        log.info("手动触发 Pixiv 收藏夹同步...");
        bot.sendMsg(event, "正在同步 Pixiv 收藏夹，请稍候...", false);
        try {
            pixivBookmarkService.syncBookmarks();
            bot.sendMsg(event, "Pixiv 收藏夹同步完成！", false);
        } catch (Exception e) {
            throw new BotException("同步 Pixiv 收藏夹失败: " + e.getMessage());
        }
    }


    @Limit(globalPermits = 20, userPermits = 3 , timeInSeconds = 3)
    @Async
    @PluginFunction(name = "鼠鼠の收藏",
            description = "从鼠鼠的收藏夹中随机抽取一张作品，发送 \"鼠鼠的收藏\" 命令即可获得~",
            permission = Permission.USER,
            autoGenerateHelp = false,
            commands = {"/鼠鼠的收藏", "/酒狐的收藏", "来份酒狐的收藏", "来10个酒狐的收藏"}
    )
    @Order(10)
    @AnyMessageHandler
    @MessageHandlerFilter(types = MsgTypeEnum.text, cmd = "^/?(?:来)?\\s*(?:(\\S*)\\s*(?:个|份|张|点)\\s*)?(鼠鼠|酒狐)的收藏$")
    public void getRandomBookmark(Bot bot, AnyMessageEvent event, Matcher matcher) {
        Long userId = event.getUserId();
        Long groupId = event.getGroupId();
        String numStr = matcher.group(1);
        int num = NumberParserUtil.parseCount(numStr);
        if (num < 1 || num > 5) {
            String msg = (num == -1)
                    ? "数量解析失败，请使用数字或中文数字表示正确的数量哦~，图片数量必须在1-5之间"
                    : (num > 5 ? "一次最多只能获取5张哦~" : "图片数量必须在1-5之间哦~");
            bot.sendMsg(event, msg, false);
            return;
        }

        try {
            for (int i = 0; i < num; i++) {
                // 1. 随机获取一个收藏
                Optional<PixivBookmark> bookmarkOptional = pixivBookmarkService.getRandomBookmark(userId,groupId);
                if (bookmarkOptional.isEmpty()) {
                    bot.sendMsg(event, "收藏夹是空的哦，还没法抽卡呢~", false);
                    return; // 收藏夹为空，直接退出
                }
                PixivBookmark bookmark = bookmarkOptional.get();
                String pid = bookmark.getId();
                // 2. 获取作品的详细信息
                PixivArtworkInfo pixivArtworkInfo = pixivService.getPixivArtworkInfo(pid);
                // 3. 异步下载图片文件
                List<File> files = pixivService.fetchImages(pid).join();
                // 4. 调用统一的发送服务
                pixivArtworkService.sendArtwork(pixivArtworkInfo, files, null);
                log.info("用户 [{}] 的随机收藏发送完成，作品ID: {}。", event.getUserId(), pid);
            }
        } catch (Exception e) {
            log.error("网络异常，获取随机收藏失败: {}", e.getMessage(), e);
            throw new BotException("获取随机收藏失败");
        }
    }


    @Async
    @PluginFunction(name = "收藏P站作品",
            description = "收藏单个Pixiv作品，支持PID或链接。用法：收藏 12345678 或 收藏 https://pixiv.net/artworks/...",
            permission = Permission.SUPERADMIN,
            autoGenerateHelp = true,
            commands = {"/收藏"}
    )
    @AnyMessageHandler
    @MessageHandlerFilter(types = MsgTypeEnum.text, cmd = "^/收藏\\s*(.+)$")
    public void addSingleBookmark(Bot bot, AnyMessageEvent event, Matcher matcher) {
        String arg = matcher.group(1).trim();
        // 解析 PID
        String pid = PixivUtils.extractPID(arg);

        if (pid == null) {
            bot.sendMsg(event, "无法从输入中提取有效的 Pixiv 作品 ID。", false);
            return;
        }

        bot.sendMsg(event, "正在收藏作品 ID: " + pid + " ...", false);
        try {
            boolean success = pixivBookmarkService.addBookmark(pid, 0); // 0 为公开
            if (success) {
                bot.sendMsg(event, "✅ 成功收藏作品: " + pid, false);
            } else {
                bot.sendMsg(event, "❌ 收藏失败，请检查日志 (可能是PID无效或Cookie过期)。", false);
            }
        } catch (Exception e) {
            log.error("收藏指令执行异常", e);
            bot.sendMsg(event, "操作发生异常: " + e.getMessage(), false);
        }
    }

    @Async
    @PluginFunction(name = "移除P站收藏",
            description = "移除单个Pixiv作品收藏，支持PID或链接。用法：取消收藏 12345678",
            permission = Permission.SUPERADMIN,
            autoGenerateHelp = true,
            commands = {"/取消收藏", "/移除收藏"}
    )
    @AnyMessageHandler
    @MessageHandlerFilter(types = MsgTypeEnum.text, cmd = "^/(取消|移除)收藏\\s*(.+)$")
    public void removeSingleBookmark(Bot bot, AnyMessageEvent event, Matcher matcher) {
        String arg = matcher.group(2).trim(); // group 1 是 (取消|移除)，group 2 是参数
        // 解析 PID
        String pid = PixivUtils.extractPID(arg);

        if (pid == null) {
            bot.sendMsg(event, "无法从输入中提取有效的 Pixiv 作品 ID。", false);
            return;
        }

        bot.sendMsg(event, "正在移除作品收藏 ID: " + pid + " ...", false);
        try {
            boolean success = pixivBookmarkService.removeBookmark(pid);
            if (success) {
                bot.sendMsg(event, "🗑️ 成功移除收藏: " + pid, false);
            } else {
                bot.sendMsg(event, "❌ 移除失败，可能网络超时或 API 变更。", false);
            }
        } catch (Exception e) {
            log.error("移除收藏指令执行异常", e);
            bot.sendMsg(event, "操作发生异常: " + e.getMessage(), false);
        }
    }


    @Async
    @PluginFunction(name = "爬取画师收藏",
            description = "爬取指定画师的所有作品并加入收藏。用法：爬取收藏 123456",
            permission = Permission.SUPERADMIN, // 必须是超管权限
            autoGenerateHelp = true,
            commands = {"/全部收藏"}
    )
    @AnyMessageHandler
    @MessageHandlerFilter(types = MsgTypeEnum.text, cmd = "^/全部收藏\\s*(.+)$")
    public void crawlUserArtworks(Bot bot, AnyMessageEvent event, Matcher matcher) {
        String arg = matcher.group(1).trim();
        // 解析 uid
        String uid = PixivUtils.extractUID(arg);

        bot.sendMsg(event, "开始解析画师 [" + uid + "] 的作品列表，正在异步执行批量收藏...", false);

        try {
            int count = pixivBookmarkService.crawlUserArtworksToBookmark(uid);
            if (count > 0) {
                bot.sendMsg(event, "已增加 " + count + " 个作品到鼠鼠の收藏。", false);
            } else {
                bot.sendMsg(event, "未找到该画师的作品，或获取列表失败。", false);
            }
        } catch (Exception e) {
            log.error("爬取收藏指令执行异常", e);
            bot.sendMsg(event, "启动任务失败: " + e.getMessage(), false);
        }
    }


    @Async
    @PluginFunction(name = "转移用户收藏",
            description = "将指定用户的公开收藏全部转移到机器人账号。用法：转移收藏 12345 或 用户主页链接",
            permission = Permission.SUPERADMIN,
            autoGenerateHelp = true,
            commands = {"/转移收藏", "/克隆收藏"}
    )
    @AnyMessageHandler
    @MessageHandlerFilter(types = MsgTypeEnum.text, cmd = "^/(转移|克隆)收藏\\s*(.+)$")
    public void transferBookmarks(Bot bot, AnyMessageEvent event, Matcher matcher) {
        String arg = matcher.group(2).trim();
        // 解析 uid
        String targetUserId = PixivUtils.extractUID(arg);

        if (targetUserId == null) {
            bot.sendMsg(event, "无法提取有效的用户 ID。请输入纯数字 ID 或用户主页链接。", false);
            return;
        }

        bot.sendMsg(event, "🔍 正在扫描用户 [" + targetUserId + "] 的公开收藏列表，请稍候...", false);

        try {
            int count = pixivBookmarkService.transferUserBookmarks(targetUserId);
            if (count > 0) {
                bot.sendMsg(event, "📦 转移完成！共转移 " + count + " 个公开收藏。", false);
            } else {
                bot.sendMsg(event, "⚠️ 未找到该用户的公开收藏，可能是用户设置了隐私，或者 ID 错误。", false);
            }
        } catch (Exception e) {
            log.error("转移收藏指令异常", e);
            bot.sendMsg(event, "操作失败: " + e.getMessage(), false);
        }
    }

    //endregion

}
