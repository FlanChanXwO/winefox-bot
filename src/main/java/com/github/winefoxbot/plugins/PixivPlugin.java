package com.github.winefoxbot.plugins;

import cn.hutool.core.lang.UUID;
import com.github.winefoxbot.annotation.PluginFunction;
import com.github.winefoxbot.model.dto.pixiv.PixivDetail;
import com.github.winefoxbot.model.entity.PixivRankPushSchedule;
import com.github.winefoxbot.model.enums.Permission;
import com.github.winefoxbot.model.enums.PixivArtworkType;
import com.github.winefoxbot.model.enums.PixivRankPushMode;
import com.github.winefoxbot.service.pixiv.PixivRankPushScheduleService;
import com.github.winefoxbot.service.pixiv.PixivRankService;
import com.github.winefoxbot.service.pixiv.PixivService;
import com.github.winefoxbot.utils.DocxUtil;
import com.github.winefoxbot.utils.FileUtil;
import com.github.winefoxbot.utils.PdfUtil;
import com.mikuac.shiro.annotation.AnyMessageHandler;
import com.mikuac.shiro.annotation.MessageHandlerFilter;
import com.mikuac.shiro.annotation.common.Shiro;
import com.mikuac.shiro.common.utils.MsgUtils;
import com.mikuac.shiro.common.utils.ShiroUtils;
import com.mikuac.shiro.core.Bot;
import com.mikuac.shiro.dto.action.common.ActionData;
import com.mikuac.shiro.dto.action.common.ActionRaw;
import com.mikuac.shiro.dto.action.common.MsgId;
import com.mikuac.shiro.dto.action.response.GroupFilesResp;
import com.mikuac.shiro.dto.event.message.AnyMessageEvent;
import com.mikuac.shiro.enums.MsgTypeEnum;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
import org.apache.commons.io.FileUtils;
import org.apache.tomcat.util.buf.StringUtils;
import org.jobrunr.scheduling.cron.Cron;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import javax.net.ssl.SSLHandshakeException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.DayOfWeek;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;

@Component
@Slf4j
@Shiro
@RequiredArgsConstructor
public class PixivPlugin {

    private final PixivService pixivService;
    private final PixivRankService pixivRankService;
    private final PixivRankPushScheduleService pixivRankPushScheduleService;
    private static final String FILE_OUTPUT_DIR = "data/files/pixiv/wrappers";

    @PluginFunction(group = "Pixiv", name = "查看P站排行订阅状态", description = "查看当前群聊的P站排行订阅状态。", commands = {"/查看P站排行订阅", "/p站订阅状态"})
    @AnyMessageHandler
    @MessageHandlerFilter(cmd = "^/(查看P站排行订阅|p站订阅状态)$")
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

    @PluginFunction(group = "Pixiv", name = "订阅P站排行",
            permission = Permission.ADMIN,
            description = "订阅P站排行榜推送。用法: /订阅P站排行榜 [类型] [时间]。类型支持\"每日\"，\"每周\"，\"每月\", 例如: /订阅P站排行榜 每日 09:30", commands = {"/订阅P站排行榜"})
    @AnyMessageHandler
    @MessageHandlerFilter(cmd = "^/订阅P站排行榜(?:\\s+(每日|每周|每月))?(?:\\s+([0-2][0-9]):([0-5][0-9]))?$")
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


    @PluginFunction(group = "Pixiv", name = "取消P站排行榜订阅",
            permission = Permission.ADMIN,
            description = "取消订阅P站排行榜。用法: /取消P站排行榜 [类型]", commands = {"/取消P站排行榜 每日", "/取消P站排行榜 每周", "/取消P站排行榜 每月"})
    @AnyMessageHandler
    @MessageHandlerFilter(cmd = "^/取消P站排行榜\\s+(每日|每周|每月)$")
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
    @PluginFunction(group = "Pixiv", name = "Pixiv 图片获取", description = "使用 /p <PID 或 URL> 命令获取 Pixiv 作品图片。", commands = {"/p <PID 或 URL>"})
    @AnyMessageHandler
    @MessageHandlerFilter(types = MsgTypeEnum.text, cmd = "^/(p|P|pixiv)(?:\\s+(\\S+))?$")
    public void getPixivPic(Bot bot, AnyMessageEvent event, Matcher matcher) {
        String arg = matcher.group(2);
        boolean isInGroup = event.getGroupId() != null;
        Integer messageId = event.getMessageId();
        // 校验 URL/PID
        if (!pixivService.isPixivURL(arg) && !arg.matches("\\d+")) {
            return;
        }

        String pid = pixivService.extractPID(arg);

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
            pixivDetail = pixivService.getPixivArtworkDetail(pid);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        String fileName = null;
        String filePath= null;
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
            if (pixivDetail.getIsR18()) {
                bot.sendMsg(event, builder.build(), false); // 先发送作品信息

                // --- 这是新的核心逻辑 ---
                long totalSize = 0;
                for (File file : files) {
                    if (file.exists()) {
                        totalSize += file.length();
                    }
                }

                final long twentyMB = 20 * 1024 * 1024;

                try {
                    if (totalSize >= twentyMB) {
                        // --- 分支1: 总大小超过20MB，打包成ZIP ---
                        log.info("图片总大小 {}MB 超过20MB阈值，将打包成ZIP文件。", String.format("%.2f", totalSize / (1024.0 * 1024.0)));
                        filePath = createZipArchive(files, FILE_OUTPUT_DIR, "pixiv_" + pid);
                        if (filePath == null) {
                            log.error("创建R18 ZIP压缩包失败（文件列表为空）, pid={}", pid);
                            bot.sendMsg(event, "生成R18压缩包失败，请稍后重试。", false);
                            return;
                        }
                    } else {
                        // --- 分支2: 总大小未超过20MB，按原方式生成DOCX/PDF ---
                        log.info("图片总大小 {}MB 未超过20MB阈值，按原方式处理。", String.format("%.2f", totalSize / (1024.0 * 1024.0)));
                        List<byte[]> imageBytes = new ArrayList<>();
                        for (File file : files) {
                            imageBytes.add(FileUtils.readFileToByteArray(file));
                        }

                        filePath = pixivDetail.getType() == PixivArtworkType.GIF
                                ? DocxUtil.wrapImagesIntoDocx(imageBytes, FILE_OUTPUT_DIR)
                                : PdfUtil.wrapByteImagesIntoPdf(imageBytes, FILE_OUTPUT_DIR);

                        if (filePath == null) {
                            log.error("生成R18 DOCX/PDF文件失败, pid={}", pid);
                            bot.sendMsg(event, "生成R18文件包失败，请稍后重试。", false);
                            return;
                        }
                    }
                } catch (IOException e) {
                    log.error("处理R18文件时发生IO异常, pid={}", pid, e);
                    bot.sendMsg(event, "处理文件时发生内部错误，请稍后重试。", false);
                    return;
                }

                // --- 公共的上传逻辑 ---
                Path finalPath = Paths.get(filePath);
                fileName = finalPath.getFileName().toString();
                ActionRaw actionRaw = isInGroup
                        ? bot.uploadGroupFile(event.getGroupId(), finalPath.toAbsolutePath().toString(), fileName)
                        : bot.uploadPrivateFile(event.getUserId(), finalPath.toAbsolutePath().toString(), fileName);
                if (actionRaw.getRetCode() != 0) {
                    log.error("上传 Pixiv R18 文件失败，pid={}, file={}, retcode={}, message={}", pid, filePath, actionRaw.getRetCode(), actionRaw.getStatus());
                } else {
                    log.info("已成功发起 Pixiv R18 文件上传, pid={}, file={}", pid, filePath);
                }
            } else {
                for (File file : files) {
                    String imgFilePath = FileUtil.getFileUrlPrefix() + file.getAbsolutePath();
                    builder.img(imgFilePath);
                }
                ActionData<MsgId> sendResp = bot.sendMsg(event, builder.build(), false);
                int retryTimes = 3;
                while (sendResp.getRetCode() != 0 && retryTimes-- > 0) {
                    log.warn("发送 Pixiv 图片失败，正在重试，剩余次数={}，pid={}", retryTimes, pid);
                    sendResp = bot.sendMsg(event, builder.build(), false);
                }
                if (sendResp.getRetCode() != 0) {
                    log.error("发送 Pixiv 图片最终失败，pid={}", pid);
                }
            }

        } catch (Exception e) {
            log.error("处理 Pixiv 图片失败 pid={}", pid, e);
            bot.sendMsg(event, MsgUtils.builder()
                    .reply(messageId)
                    .text("处理 Pixiv 图片失败：" + e.getMessage())
                    .build(), false);
        } finally {
            clearFile(filePath);
            if (isInGroup) {
                deleteGroupFile(bot, event, fileName);
            }
        }
    }

    /**
     * 将一组文件打包成一个ZIP压缩文件。(这个辅助方法保持不变)
     *
     * @param filesToZip 要压缩的文件列表
     * @param outputDir  ZIP文件的输出目录
     * @param baseName   ZIP文件的基础名称 (不含.zip后缀)
     * @return 创建的ZIP文件的绝对路径, 如果文件列表为空则返回null
     * @throws IOException 如果发生IO错误
     */
    private String createZipArchive(List<File> filesToZip, String outputDir, String baseName) throws IOException {
        if (filesToZip == null || filesToZip.isEmpty()) {
            return null;
        }

        String zipFileName = baseName + "_" + UUID.randomUUID().toString().substring(0, 8) + ".zip";
        String zipFilePath = Paths.get(outputDir, zipFileName).toString();

        log.info("开始创建ZIP文件: {}", zipFilePath);

        try (FileOutputStream fos = new FileOutputStream(zipFilePath);
             ZipArchiveOutputStream zaos = new ZipArchiveOutputStream(fos)) {

            for (File file : filesToZip) {
                if (file.exists() && file.isFile()) {
                    ZipArchiveEntry entry = new ZipArchiveEntry(file.getName());
                    zaos.putArchiveEntry(entry);
                    try (FileInputStream fis = new FileInputStream(file)) {
                        byte[] buffer = new byte[8192];
                        int len;
                        while ((len = fis.read(buffer)) > 0) {
                            zaos.write(buffer, 0, len);
                        }
                    }
                    zaos.closeArchiveEntry();
                }
            }
        }

        log.info("ZIP文件创建成功: {}", zipFilePath);
        return zipFilePath;
    }




    private void clearFile(String filePath) {
        if (filePath != null) {
            try {
                Files.deleteIfExists(Paths.get(filePath));
                log.info("已删除临时文件: {}", filePath);
            } catch (IOException e) {
                log.warn("删除临时文件失败: {}", filePath, e);
            }
        }
    }

    @Async
    protected void deleteGroupFile(Bot bot, AnyMessageEvent anyMessageEvent, String fileName) {
        try {
            TimeUnit.SECONDS.sleep(30);
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
    @PluginFunction(group = "Pixiv", name = "Pixiv 排行榜获取", description = "获取 Pixiv 排行榜前6名插画作品。", commands = {"/p站本日排行榜", "/P站本日排行榜", "/p站本周排行榜", "/P站本周排行榜", "/p站本月排行榜", "/P站本月排行榜"})
    @AnyMessageHandler
    @MessageHandlerFilter(types = MsgTypeEnum.text, cmd = "^/((p|P)站(本|今)(日|周|月)排行榜|pr(d|w|m))(?:\\s+(\\S+))?$")
    public void getPixivRankByType(Bot bot, AnyMessageEvent event, Matcher matcher) {
        bot.sendMsg(event, "正在获取 Pixiv 排行榜，请稍候...", false);
        String rankType = matcher.group(4); // 日, 周, 月
        String params = matcher.group(6);
        PixivRankPushMode mode;
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
