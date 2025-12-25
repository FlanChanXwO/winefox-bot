package com.github.winefoxbot.plugins;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.winefoxbot.annotation.PluginFunction;
import com.github.winefoxbot.exception.PixivR18Exception;
import com.github.winefoxbot.model.dto.pixiv.PixivDetail;
import com.github.winefoxbot.model.dto.pixiv.PixivPushTarget;
import com.github.winefoxbot.model.entity.ScheduleTask;
import com.github.winefoxbot.model.enums.ScheduleType;
import com.github.winefoxbot.model.enums.TaskStatus;
import com.github.winefoxbot.schedule.DynamicTaskScheduler;
import com.github.winefoxbot.schedule.task.PixivRankPushExecutor;
import com.github.winefoxbot.service.pixiv.PixivRankService;
import com.github.winefoxbot.service.pixiv.PixivService;
import com.github.winefoxbot.service.task.ScheduleTaskService;
import com.github.winefoxbot.utils.FileUtil;
import com.github.winefoxbot.utils.MethodReferenceUtil;
import com.github.winefoxbot.utils.SpringBeanUtil;
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
import org.apache.commons.io.FileUtils;
import org.apache.tomcat.util.buf.StringUtils;
import org.springframework.context.ApplicationContext;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Component;

import javax.net.ssl.SSLHandshakeException;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
@Component
@Slf4j
@Shiro
@RequiredArgsConstructor
public class PixivPlugin {

    private final PixivService pixivService;
    private final PixivRankService pixivRankService;
    private final ApplicationContext applicationContext;
    private final ScheduleTaskService scheduleTaskService;
    private final ObjectMapper objectMapper; // 用于处理 JSON 参数
    private final DynamicTaskScheduler taskScheduler;

    private static final AtomicBoolean isFetchRank = new AtomicBoolean(false);
    private static final String RANK_SUB_DESCRIPTION_PREFIX = "PIXIV_RANK_SUB_";
    private static final String DEFAULT_CRON_FOR_RANK = "0 0 12 * * ?"; // 默认每天中午12点


    @PluginFunction(group = "Pixiv", name = "查看P站排行订阅状态", description = "查看当前会话是否已订阅每日P站排行推送。", commands = {"/查看P站排行订阅", "/p站订阅状态"})
    @AnyMessageHandler
    @MessageHandlerFilter(cmd = "^(/查看P站排行订阅|/p站订阅状态)$")
    public void checkRankPushSubscription(Bot bot, AnyMessageEvent event) {
        String targetType = event.getGroupId() != null ? "group" : "private";
        long targetId = event.getGroupId() != null ? event.getGroupId() : event.getUserId();

        // 使用我们之前定义的常量来构建唯一的任务描述
        String taskId = RANK_SUB_DESCRIPTION_PREFIX + targetType.toUpperCase() + "_" + targetId;

        // 调用服务层方法来查询任务
        Optional<ScheduleTask> taskOptional = scheduleTaskService.findTaskByTaskId(taskId);

        if (taskOptional.isPresent()) {
            // 如果找到了有效的任务
            ScheduleTask task = taskOptional.get();
            // 从 Cron 表达式中解析出时间
            try {
                CronExpression.parse(task.getCronExpression());
                // 这里假设 Cron 表达式格式为 "0 minute hour * * ?"
                String[] fields = task.getCronExpression().split(" ");
                String minute = fields[1];
                String hour = fields[2];

                String reply = String.format(
                        "本会话已订阅P站排行推送。\n推送时间：每天 %s:%s\n任务状态：%s",
                        hour, minute, "已启用" // 因为我们只查询 PENDING 状态，所以这里可以硬编码为“已启用”
                );
                bot.sendMsg(event, reply, true);
            } catch (IllegalArgumentException e) {
                // 如果 Cron 表达式格式不正确，提供一个备用消息
                log.error("数据库中任务 [ID: {}] 的Cron表达式 '{}' 格式无效。", task.getTaskId(), task.getCronExpression(), e);
                bot.sendMsg(event, "已订阅P站排行推送，但推送时间格式异常，请联系管理员。", true);
            }
        } else {
            // 如果没有找到有效的任务
            String reply = "本会话尚未订阅P站排行推送。\n您可以通过发送 `/开启P站排行推送` 来订阅。";
            bot.sendMsg(event, reply, true);
        }
    }

//    @PluginFunction(group = "Pixiv", name = "开启P站排行推送", description = "订阅每日中午12点的Pixiv排行榜推送。", commands = {"/开启P站排行推送"})
    @AnyMessageHandler
    @MessageHandlerFilter(cmd = "^/(开启P站排行推送|订阅P站排行)$")
    public void subscribeRankPush(Bot bot, AnyMessageEvent event) {
        String targetType = event.getGroupId() != null ? "group" : "private";
        long targetId = event.getGroupId() != null ? event.getGroupId() : event.getUserId();
        // 使用我们之前定义的常量来构建唯一的任务描述
        String taskId = RANK_SUB_DESCRIPTION_PREFIX + targetType.toUpperCase() + "_" + targetId;
        Long groupId = event.getGroupId();
        pixivService.addSchedulePush(groupId);

        // 调用服务层方法来查询任务
        Optional<ScheduleTask> taskOptional = scheduleTaskService.findTaskByTaskId(taskId);
        if (taskOptional.isPresent() && taskOptional.get().getStatus() == TaskStatus.PENDING) {
            bot.sendMsg(event, "本会话已开启P站排行推送，无需重复订阅。", true);
            return;
        }

        try {
            PixivPushTarget target = new PixivPushTarget();
            target.setTargetId(targetId);
            target.setTargetType(targetType);
            String taskParams = objectMapper.writeValueAsString(target);
            ScheduleTask task = ScheduleTask.builder()
                    .description("Pixiv每日排行榜推送到" + targetType + "_" + targetId)
                    .taskId(taskId) // 使用唯一任务ID，防止重复订阅
                    .beanName(SpringBeanUtil.getBeanName(applicationContext, PixivRankPushExecutor.class)) // Spring Bean的名称，通常是类名首字母小写
                    .methodName(MethodReferenceUtil.getMethodName(PixivRankPushExecutor::execute))
                    .taskParams(taskParams)
                    .scheduleType(ScheduleType.RECURRING_INDEFINITE)
                    .cronExpression(DEFAULT_CRON_FOR_RANK)
                    .build();

            scheduleTaskService.createScheduleTask(task);
            bot.sendMsg(event, "成功订阅P站每日排行榜！将会在每天中午12点进行推送。", true);
        } catch (Exception e) {
            log.error("创建P站排行推送任务失败", e);
            bot.sendMsg(event, "订阅失败，请联系管理员查看后台日志。", true);
        }
    }

//    @PluginFunction(group = "Pixiv", name = "关闭P站排行推送", description = "取消订阅每日的Pixiv排行榜推送。", commands = {"/关闭P站排行推送"})
    @AnyMessageHandler
    @MessageHandlerFilter(cmd = "^/(关闭P站排行推送|取消P站排行)$")
    public void unsubscribeRankPush(Bot bot, AnyMessageEvent event) {
        String targetType = event.getGroupId() != null ? "group" : "private";
        long targetId = event.getGroupId() != null ? event.getGroupId() : event.getUserId();
        String taskId = RANK_SUB_DESCRIPTION_PREFIX + targetType.toUpperCase() + "_" + targetId;

        // 调用服务层方法来查询任务
        Optional<ScheduleTask> taskOptional = scheduleTaskService.findTaskByTaskId(taskId);
        if (taskOptional.isEmpty() || taskOptional.get().getStatus() != TaskStatus.PENDING) {
            bot.sendMsg(event, "本会话未开启P站排行推送，无需关闭。", true);
            return;
        }

        // 调用我们之前设计的取消服务
        boolean success = scheduleTaskService.cancelScheduleTask(taskOptional.get().getTaskId(), String.valueOf(event.getUserId()));

        if (success) {
            bot.sendMsg(event, "已成功关闭P站排行推送。", true);
        } else {
            bot.sendMsg(event, "关闭失败，请稍后再试或联系管理员。", true);
        }
    }


//    @PluginFunction(group = "Pixiv", name = "修改P站排行推送时间", description = "修改每日推送时间，格式为 HH:mm (24小时制)。", commands = {"/修改P站排行推送时间 HH:mm"})
    @AnyMessageHandler
    @MessageHandlerFilter(cmd = "^/修改P站排行推送时间\\s+([0-2][0-9]):([0-5][0-9])$")
    public void updateRankPushTime(Bot bot, AnyMessageEvent event, Matcher matcher) {
        String targetType = event.getGroupId() != null ? "group" : "private";
        long targetId = event.getGroupId() != null ? event.getGroupId() : event.getUserId();
        String taskId = RANK_SUB_DESCRIPTION_PREFIX + targetType.toUpperCase() + "_" + targetId;

        Optional<ScheduleTask> taskOptional = scheduleTaskService.findTaskByTaskId(taskId);
        if (taskOptional.isEmpty() || taskOptional.get().getStatus() != TaskStatus.PENDING) {
            bot.sendMsg(event, "本会话未开启P站排行推送，无法修改时间。", true);
            return;
        }

        try {
            int hour = Integer.parseInt(matcher.group(1));
            int minute = Integer.parseInt(matcher.group(2));

            // 构建新的Cron表达式
            String newCron = String.format("0 %d %d * * ?", minute, hour);
            ScheduleTask task = taskOptional.get();
            task.setCronExpression(newCron);
            // 重新计算下一次执行时间
            scheduleTaskService.calculateAndSetNextExecutionTime(task);
            // 更新数据库
            scheduleTaskService.updateById(task);
            // 重新调度任务（内部会先取消旧的）
            taskScheduler.scheduleTask(task); // 你需要注入 DynamicTaskScheduler

            bot.sendMsg(event, String.format("推送时间已成功修改为每天的 %02d:%02d。", hour, minute), true);
        } catch (Exception e) {
            log.error("修改P站排行推送时间失败", e);
            bot.sendMsg(event, "时间修改失败，请确保格式正确或联系管理员。", true);
        }
    }




    @Async
    @PluginFunction(group = "Pixiv", name = "Pixiv 图片获取", description = "使用 /p <PID 或 URL> 命令获取 Pixiv 作品图片。", commands = {"/p <PID 或 URL>"})
    @AnyMessageHandler
    @MessageHandlerFilter(types = MsgTypeEnum.text, cmd = "^/(p|P|pixiv)(?:\\s+(\\S+))?$")
    public void getPixivPic(Bot bot, AnyMessageEvent event, Matcher matcher) {
        String arg = matcher.group(2);
        Integer messageId = event.getMessageId();
        // 校验 URL/PID
        if (!pixivService.isPixivURL(arg) && !arg.matches("\\d+")) {
            return;
        }

        String pid = pixivService.extractPID(arg);
        Long groupId = event.getGroupId();

        try {
            if (pid == null || !pixivService.isValidPixivPID(pid)) {
                bot.sendMsg(event, MsgUtils.builder()
                        .reply(messageId)
                        .text("无效的 Pixiv PID 或 URL！")
                        .build(), false);
                return;
            }
        } catch (SSLHandshakeException e) {
            log.error("Pixiv SSL 握手失败，可能是 Pixiv 证书发生变更导致，请检查！", e);
            bot.sendMsg(event, MsgUtils.builder()
                    .reply(messageId)
                    .text("因为网络问题，图片获取失败，请重试")
                    .build(), false);
            return;
        } catch (IOException e) {
            log.error("输入流异常，获取 Pixiv PID={} 失败", pid, e);
            bot.sendMsg(event, MsgUtils.builder()
                    .reply(messageId)
                    .text("因为网络问题，图片获取失败，请重试")
                    .build(), false);
            return;
        }
        bot.sendMsg(event, MsgUtils.builder()
                .reply(messageId)
                .text("正在处理 Pixiv 图片，请稍候...")
                .build(), false);


        PixivDetail pixivDetail = null;
        try {
            boolean isR18Artwork = pixivService.isR18Artwork(pid, groupId);
            if (!isR18Artwork) {
                pixivDetail = pixivService.getPixivArtworkDetail(pid);
            }
        } catch (PixivR18Exception e) {
            bot.sendMsg(event, MsgUtils.builder()
                    .reply(messageId)
                    .text(e.getMessage())
                    .build(), false);
            return;
        } catch (IOException e) {
            log.error("获取 Pixiv 作品信息失败 pid={}", pid, e);
            bot.sendMsg(event, MsgUtils.builder()
                    .reply(messageId)
                    .text("获取 Pixiv 作品信息失败：" + e.getMessage())
                    .build(), false);
            return;
        }

        // 下载并发送
        try {
            List<File> files = pixivService.fetchImages(pid).join();
            if (files == null || files.isEmpty()) {
                bot.sendMsg(event, MsgUtils.builder()
                        .reply(messageId)
                        .text("未能获取到图片文件！")
                        .build(), false);
                return;
            }
            MsgUtils builder = MsgUtils.builder();
            builder.text(String.format("""
                            作品标题：%s (%s)
                            作者：%s (%s)
                            描述信息：%s
                            作品链接：https://www.pixiv.net/artworks/%s
                            标签：%s
                            """, pixivDetail.getTitle(), pixivDetail.getPid(),
                    pixivDetail.getUserName(), pixivDetail.getUid(),
                    pixivDetail.getDescription(),
                    pixivDetail.getPid(),
                    StringUtils.join(pixivDetail.getTags(), ',')));
            for (File file : files) {
                String filePath = FileUtil.getFileUrlPrefix() + file.getAbsolutePath();
                builder.img(filePath);
            }
            bot.sendMsg(event, builder.build(), false);
            File parentFile = files.getFirst().getParentFile();
            // 删除文件
            if (parentFile.exists()) {
                FileUtils.deleteDirectory(parentFile);
            }
        } catch (Exception e) {
            log.error("处理 Pixiv 图片失败 pid={}", pid, e);
            bot.sendMsg(event, MsgUtils.builder()
                    .reply(messageId)
                    .text("处理 Pixiv 图片失败：" + e.getMessage())
                    .build(), false);
        }
    }

    @Async
    @PluginFunction(group = "Pixiv", name = "Pixiv 今日排行榜获取", description = "获取 Pixiv 当日排行榜前6名作品。如果不指定参数，则默认查询插画", commands = {"/p站本日排行榜", "/P站本日排行榜"})
    @AnyMessageHandler
    @MessageHandlerFilter(types = MsgTypeEnum.text, cmd = "^/((p|P)站(本|今)日排行榜|prd)(?:\\s+(\\S+))?$")
    public void getRankToday(Bot bot, AnyMessageEvent event, Matcher matcher) {
        if (isFetchRank.get()) {
            bot.sendMsg(event, "当前已有排行榜获取任务在进行中，请稍后再试。", false);
            return;
        }
        isFetchRank.set(true);
        bot.sendMsg(event, "正在获取 Pixiv 今日排行榜，请稍候...", false);
        String params = matcher.group(2);
        PixivRankService.Content content = params != null ? PixivRankService.Content.valueOf(params.toUpperCase()) : PixivRankService.Content.ILLUST;
        getPixivRank(bot, event, matcher, PixivRankService.Mode.DAILY, content, false);
    }

    @Async
    @PluginFunction(group = "Pixiv", name = "Pixiv 今日R18排行榜获取", description = "获取 Pixiv 当日r18排行榜前6名作品。如果不指定参数，则默认查询插画", commands = {"/p站本日排行榜", "/P站本日排行榜"})
    @AnyMessageHandler
    @MessageHandlerFilter(types = MsgTypeEnum.text, cmd = "^/((p|P)站(本|今)日r18排行榜|prd18)(?:\\s+(\\S+))?$")
    public void getRankR18Today(Bot bot, AnyMessageEvent event, Matcher matcher) {
        if (isFetchRank.get()) {
            bot.sendMsg(event, "当前已有排行榜获取任务在进行中，请稍后再试。", false);
            return;
        }
        isFetchRank.set(true);
        bot.sendMsg(event, "正在获取 Pixiv 今日排行榜，请稍候...", false);
        String params = matcher.group(2);
        PixivRankService.Content content = params != null ? PixivRankService.Content.valueOf(params.toUpperCase()) : PixivRankService.Content.ILLUST;
        getPixivRank(bot, event, matcher, PixivRankService.Mode.DAILY, content, true);
    }


    @Async
    @PluginFunction(group = "Pixiv", name = "Pixiv 本周排行榜获取", description = "获取 Pixiv 当周排行榜前6名作品。如果不指定参数，则默认查询插画", commands = {"/p站本日排行榜", "/P站本日排行榜"})
    @AnyMessageHandler
    @MessageHandlerFilter(types = MsgTypeEnum.text, cmd = "^/((p|P)站(本|今)周排行榜|prw)(?:\\s+(\\S+))?$")
    public void getRankWeek(Bot bot, AnyMessageEvent event, Matcher matcher) {
        if (isFetchRank.get()) {
            bot.sendMsg(event, "当前已有排行榜获取任务在进行中，请稍后再试。", false);
            return;
        }
        isFetchRank.set(true);
        bot.sendMsg(event, "正在获取 Pixiv 本周排行榜，请稍候...", false);
        String params = matcher.group(2);
        PixivRankService.Content content = params != null ? PixivRankService.Content.valueOf(params.toUpperCase()) : PixivRankService.Content.ILLUST;
        getPixivRank(bot, event, matcher, PixivRankService.Mode.WEEKLY, content, false);
    }

    @Async
    @PluginFunction(group = "Pixiv", name = "Pixiv 本周R18排行榜获取", description = "获取 Pixiv 当周排行榜前6名作品。如果不指定参数，则默认查询插画", commands = {"/p站本日排行榜", "/P站本日排行榜"})
    @AnyMessageHandler
    @MessageHandlerFilter(types = MsgTypeEnum.text, cmd = "^/((p|P)站(本|今)周r18排行榜|prw18)(?:\\s+(\\S+))?$")
    public void getRankR18Week(Bot bot, AnyMessageEvent event, Matcher matcher) {
        if (isFetchRank.get()) {
            bot.sendMsg(event, "当前已有排行榜获取任务在进行中，请稍后再试。", false);
            return;
        }
        isFetchRank.set(true);
        bot.sendMsg(event, "正在获取 Pixiv 本周排行榜，请稍候...", false);
        String params = matcher.group(2);
        PixivRankService.Content content = params != null ? PixivRankService.Content.valueOf(params.toUpperCase()) : PixivRankService.Content.ILLUST;
        getPixivRank(bot, event, matcher, PixivRankService.Mode.WEEKLY, content, true);
    }

    @Async
    @PluginFunction(group = "Pixiv", name = "Pixiv 本月排行榜获取", description = "获取 Pixiv 当月排行榜前6名作品。如果不指定参数，则默认查询插画", commands = {"/p站本日排行榜", "/P站本日排行榜"})
    @AnyMessageHandler
    @MessageHandlerFilter(types = MsgTypeEnum.text, cmd = "^/((p|P)站(本|今)月排行榜|prm)(?:\\s+(\\S+))?$")
    public void getRankMonth(Bot bot, AnyMessageEvent event, Matcher matcher) {
        if (isFetchRank.get()) {
            bot.sendMsg(event, "当前已有排行榜获取任务在进行中，请稍后再试。", false);
            return;
        }
        isFetchRank.set(true);
        bot.sendMsg(event, "正在获取 Pixiv 本月排行榜，请稍候...", false);
        String params = matcher.group(2);
        PixivRankService.Content content = params != null ? PixivRankService.Content.valueOf(params.toUpperCase()) : PixivRankService.Content.ILLUST;
        getPixivRank(bot, event, matcher, PixivRankService.Mode.WEEKLY, content, false);
    }


    @Async
    @PluginFunction(group = "Pixiv", name = "Pixiv 本月排行榜获取", description = "获取 Pixiv 当月排行榜前6名作品。如果不指定参数，则默认查询插画", commands = {"/p站本日排行榜", "/P站本日排行榜"})
    @AnyMessageHandler
    @MessageHandlerFilter(types = MsgTypeEnum.text, cmd = "^/((p|P)站(本|今)月排行榜|prm18)(?:\\s+(\\S+))?$")
    public void getRankR18Month(Bot bot, AnyMessageEvent event, Matcher matcher) {
        if (isFetchRank.get()) {
            bot.sendMsg(event, "当前已有排行榜获取任务在进行中，请稍后再试。", false);
            return;
        }
        isFetchRank.set(true);
        bot.sendMsg(event, "正在获取 Pixiv 本月排行榜，请稍候...", false);
        String params = matcher.group(2);
        PixivRankService.Content content = params != null ? PixivRankService.Content.valueOf(params.toUpperCase()) : PixivRankService.Content.ILLUST;
        getPixivRank(bot, event, matcher, PixivRankService.Mode.MONTHLY, content, true);
    }

    private void getPixivRank(Bot bot, AnyMessageEvent event, Matcher matcher , PixivRankService.Mode mode , PixivRankService.Content content , boolean isR18) {
        String params = matcher.group(2);
        Integer messageId = event.getMessageId();
        try {
            List<String> msgList = new ArrayList<>();
            List<String> rankIds = pixivRankService.getRank(mode, content, isR18);
            List<List<File>> filesList = new ArrayList<>();
            for (String rankId : rankIds) {
                List<File> files = pixivService.fetchImages(rankId).join();
                if (files.isEmpty()) {
                    continue;
                }
                PixivDetail pixivDetail = pixivService.getPixivArtworkDetail(rankId);
                MsgUtils builder = MsgUtils.builder();
                builder.text(String.format("""
                                作品标题：%s (%s)
                                作者：%s (%s)
                                描述信息：%s
                                作品链接：https://www.pixiv.net/artworks/%s
                                标签：%s
                                """, pixivDetail.getTitle(), pixivDetail.getPid(),
                        pixivDetail.getUserName(), pixivDetail.getUid(),
                        pixivDetail.getDescription(),
                        pixivDetail.getPid(),
                        StringUtils.join(pixivDetail.getTags(), ',')));
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
            List<Map<String, Object>> forwardMsg = ShiroUtils.generateForwardMsg(bot.getSelfId(), "pixiv", msgList);
            bot.sendForwardMsg(event, forwardMsg);
            // 删除文件
            for (List<File> files : filesList) {
                File parentFile = files.getFirst().getParentFile();
                if (parentFile.exists()) {
                    FileUtils.deleteDirectory(parentFile);
                }
            }
        } catch (SSLHandshakeException e) {
            log.error("Pixiv SSL 握手失败，可能是 Pixiv 证书发生变更导致，请检查！", e);
            bot.sendMsg(event, "因为网络问题，图片获取失败，请重试", false);
        } catch (IllegalArgumentException e) {
            log.error("无效的排行榜参数: {}", params, e);
            bot.sendMsg(event, "无效的排行榜参数: " + params, false);
        } catch (Exception e) {
            log.error("处理 Pixiv 图片失败", e);
            bot.sendMsg(event, "处理 Pixiv 图片失败：" + e.getMessage(), false);
        } finally {
            isFetchRank.set(false);
            System.gc();
        }
    }

}
