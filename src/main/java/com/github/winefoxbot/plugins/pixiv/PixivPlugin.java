package com.github.winefoxbot.plugins.pixiv;

import com.github.winefoxbot.core.annotation.Plugin;
import com.github.winefoxbot.core.annotation.PluginFunction;
import com.github.winefoxbot.core.model.enums.Permission;
import com.github.winefoxbot.core.utils.FileUtil;
import com.github.winefoxbot.plugins.pixiv.model.dto.common.PixivArtworkInfo;
import com.github.winefoxbot.plugins.pixiv.model.entity.PixivRankPushSchedule;
import com.github.winefoxbot.plugins.pixiv.model.enums.PixivRankPushMode;
import com.github.winefoxbot.plugins.pixiv.service.PixivArtworkService;
import com.github.winefoxbot.plugins.pixiv.service.PixivRankPushScheduleService;
import com.github.winefoxbot.plugins.pixiv.service.PixivRankService;
import com.github.winefoxbot.plugins.pixiv.service.PixivService;
import com.mikuac.shiro.annotation.AnyMessageHandler;
import com.mikuac.shiro.annotation.MessageHandlerFilter;
import com.mikuac.shiro.annotation.common.Shiro;
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
import org.springframework.stereotype.Component;

import javax.net.ssl.SSLHandshakeException;
import java.io.File;
import java.time.DayOfWeek;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;

import static com.github.winefoxbot.core.config.app.WineFoxBotConfig.*;


@Plugin(name = "Pixiv",
        description = "提供 Pixiv 图片获取与排行榜订阅等功能",
        permission = Permission.USER,
        iconPath = "icon/pixiv.png",
        order = 13
)
@Component
@Slf4j
@Shiro
@RequiredArgsConstructor
public class PixivPlugin {

    private final PixivService pixivService;
    private final PixivRankService pixivRankService;
    private final PixivRankPushScheduleService pixivRankPushScheduleService;
    private final PixivArtworkService artworkService;

    @PluginFunction(name = "查看P站排行订阅状态", description = "查看当前群聊的P站排行订阅状态。", commands = {COMMAND_PREFIX + "查看P站排行订阅" + COMMAND_SUFFIX, COMMAND_PREFIX + "p站订阅状态" + COMMAND_SUFFIX})
    @AnyMessageHandler
    @MessageHandlerFilter(types = MsgTypeEnum.text, cmd = COMMAND_PREFIX_REGEX + "(查看P站排行订阅|p站订阅状态)" + COMMAND_SUFFIX_REGEX)
    public void checkRankPushSubscription(Bot bot, AnyMessageEvent event) {
        if (event.getGroupId() == null) {
            bot.sendMsg(event, "此功能仅限群聊使用。", true);
            return;
        }
        Long groupId = event.getGroupId();
        List<PixivRankPushSchedule> schedules = pixivRankPushScheduleService.getSchedulesByGroupId(groupId);

        if (schedules.isEmpty()) {
            bot.sendMsg(event, "本群尚未订阅任何P站排行推送。\n发送 `/订阅P站排行` 查看帮助。", true);
            return;
        }

        StringBuilder reply = new StringBuilder("本群P站排行订阅状态如下：\n");
        for (PixivRankPushSchedule schedule : schedules) {
            String readableTime = pixivRankPushScheduleService.parseCronToDescription(schedule.getCronSchedule());
            reply.append(String.format("【%s】推送时间：%s\n", schedule.getDescription(), readableTime));
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
            String help = "指令格式错误！\n" +
                    "用法: /订阅P站排行 [类型] [时间 HH:mm]\n" +
                    "支持的类型: daily(每日), weekly(每周), monthly(每月)\n" +
                    "示例:\n" +
                    "/订阅P站排行 daily 09:30\n" +
                    "/订阅P站排行 weekly 10:00 (默认为周五)\n" +
                    "/订阅P站排行 monthly 12:00 (默认为月底)";
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
                cronExpression = Cron.daily(hour, minute); // "0 mm HH * * ?"
                description = "P站每日排行榜";
                mode = PixivRankPushMode.DALLY;
                break;
            case "每周":
                cronExpression = Cron.weekly(DayOfWeek.of(5), hour, minute); // 2=Monday, "0 mm HH ? * 2"
                description = "P站每周排行榜";
                mode = PixivRankPushMode.WEEKLY;
                break;
            case "每月":
                cronExpression = Cron.lastDayOfTheMonth(hour, minute); // 1st day of month, "0 mm HH 1 * ?"
                description = "P站每月排行榜";
                mode = PixivRankPushMode.MONTHLY;
                break;
        }

        pixivRankPushScheduleService.schedulePush(groupId, mode, cronExpression, description);
        String readableTime = pixivRankPushScheduleService.parseCronToDescription(cronExpression);
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
        boolean success = pixivRankPushScheduleService.unschedulePush(groupId, mode);
        if (success) {
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
        if (arg == null || (!pixivService.isPixivURL(arg) && !arg.matches("\\d+"))) {
            return; // 忽略无效命令
        }
        String pid = pixivService.extractPID(arg);
        try {
            if (pid == null || !pixivService.isValidPixivPID(pid)) {
                bot.sendMsg(event, MsgUtils.builder().reply(messageId).text("无效的 Pixiv PID 或 URL！").build(), false);
                return;
            }
            bot.sendMsg(event, MsgUtils.builder().reply(messageId).text("正在处理 Pixiv 图片，请稍候...").build(), false);
            PixivArtworkInfo pixivArtworkInfo = pixivService.getPixivArtworkInfo(pid);
            List<File> files = pixivService.fetchImages(pid).join();
            // 调用统一的发送服务
            artworkService.sendArtwork(bot, event, pixivArtworkInfo, files, null);
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
    public void getPixivRankByType(Bot bot, AnyMessageEvent event, Matcher matcher) {
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

        // rankType 仍然可能为 null，但 switch 可以安全处理
        if (rankType == null) {
            bot.sendMsg(event, "无法识别的命令格式！", true);
            return;
        }

        switch (rankType) {
            case "日" -> mode = PixivRankPushMode.DALLY;
            case "周" -> mode = PixivRankPushMode.WEEKLY;
            case "月" -> mode = PixivRankPushMode.MONTHLY;
            default -> {
                // 这个 default 理论上不会被触发了，但作为保障保留
                bot.sendMsg(event, "无效的排行榜类型！请使用 日、周 或 月。", true);
                return;
            }
        }

        PixivRankService.Content content = params != null ? PixivRankService.Content.valueOf(params.toUpperCase()) : PixivRankService.Content.ILLUST;
        getPixivRank(bot, event, matcher, mode, content);
    }


    private void getPixivRank(Bot bot, AnyMessageEvent event, Matcher matcher, PixivRankPushMode mode, PixivRankService.Content content) {
        String params = matcher.group(2);
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
                                """, pixivArtworkInfo.title(), pixivArtworkInfo.pid(),
                        pixivArtworkInfo.userName(), pixivArtworkInfo.uid(),
                        pixivArtworkInfo.description(),
                        pixivArtworkInfo.pid(),
                        StringUtils.join(pixivArtworkInfo.tags(), ',')));
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
                return;
            }
            List<Map<String, Object>> forwardMsg = ShiroUtils.generateForwardMsg(bot, msgList);
            bot.sendForwardMsg(event, forwardMsg);
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
    }

}
