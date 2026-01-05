package com.github.winefoxbot.plugins.pixiv.service.impl;

import com.github.winefoxbot.plugins.pixiv.model.dto.common.PixivArtworkInfo;
import com.github.winefoxbot.core.model.dto.SendMsgResult;
import com.github.winefoxbot.plugins.pixiv.model.enums.PixivArtworkType;
import com.github.winefoxbot.plugins.pixiv.service.PixivArtworkService;
import com.github.winefoxbot.core.utils.BotUtils;
import com.github.winefoxbot.core.utils.DocxUtil;
import com.github.winefoxbot.core.utils.FileUtil;
import com.github.winefoxbot.core.utils.PdfUtil;
import com.mikuac.shiro.common.utils.MsgUtils;
import com.mikuac.shiro.core.Bot;
import com.mikuac.shiro.dto.event.message.AnyMessageEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
import org.apache.tomcat.util.buf.StringUtils;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
@RequiredArgsConstructor
public class PixivArtworkServiceImpl implements PixivArtworkService {

    private static final String FILE_OUTPUT_DIR = "data/files/pixiv/wrappers";
    private static final long R18_ZIP_THRESHOLD = 100 * 1024 * 1024; // 100MB

    /**
     * 统一处理并发送Pixiv作品的核心方法
     *
     * @param bot         Bot实例
     * @param event       消息事件
     * @param pixivArtworkInfo 作品详情
     * @param files       作品图片文件列表
     * @param additionalText 可选的附加文本，例如在搜索插件中提示用户继续操作
     */
    @Override
    @Async("taskExecutor") // 使用一个公共的线程池
    public void sendArtwork(Bot bot, AnyMessageEvent event, PixivArtworkInfo pixivArtworkInfo, List<File> files, String additionalText) {
        if (files == null || files.isEmpty()) {
            log.warn("PID: {} 的文件列表为空，无法发送。", pixivArtworkInfo.getPid());
            bot.sendMsg(event, "未能获取到PID: " + pixivArtworkInfo.getPid() + " 的图片文件！", false);
            return;
        }

        try {
            if (pixivArtworkInfo.getIsR18()) {
                handleR18Artwork(bot, event, pixivArtworkInfo, files);
            } else {
                handleNormalArtwork(bot, event, pixivArtworkInfo, files, additionalText);
            }
        } catch (Exception e) {
            log.error("处理并发送 PID: {} 时发生未知错误。", pixivArtworkInfo.getPid(), e);
            bot.sendMsg(event, "处理作品时发生内部错误：" + e.getMessage(), false);
        }
    }

    /**
     * 处理非R18作品
     */
    private void handleNormalArtwork(Bot bot, AnyMessageEvent event, PixivArtworkInfo pixivArtworkInfo, List<File> files, String additionalText) throws InterruptedException {
        MsgUtils builder = buildArtworkInfoMsg(pixivArtworkInfo, false); // 不包含描述
        for (File file : files) {
            builder.img(FileUtil.getFileUrlPrefix() + file.getAbsolutePath());
        }
        if (additionalText != null && !additionalText.isEmpty()){
            builder.text(additionalText);
        }

        SendMsgResult sendResp = BotUtils.sendMsgByEvent(bot, event, builder.build(), false);

        // 重试逻辑
        int retryTimes = 3;
        long delay = 1000;
        while (sendResp != null && !sendResp.isSuccess() && retryTimes-- > 0) {
            log.warn("发送 Pixiv 图片失败，正在重试，剩余次数={}，pid={}", retryTimes, pixivArtworkInfo.getPid());
            Thread.sleep(delay);
            delay *= 2; // 间隔加倍
            sendResp = BotUtils.sendMsgByEvent(bot, event, builder.build(), false);
        }

        if (sendResp == null || !sendResp.isSuccess()) {
            log.error("发送 Pixiv 图片最终失败，pid={}", pixivArtworkInfo.getPid());
            bot.sendMsg(event, "图片发送失败，请稍后重试。", false);
        }
    }

    /**
     * 处理R18作品
     */
    private void handleR18Artwork(Bot bot, AnyMessageEvent event, PixivArtworkInfo pixivArtworkInfo, List<File> files) throws IOException {
        // 1. 先发送作品文字信息
        bot.sendMsg(event, buildArtworkInfoMsg(pixivArtworkInfo, true).build(), false);

        // 2. 计算总大小
        long totalSize = files.stream().filter(File::exists).mapToLong(File::length).sum();

        // 3. 根据大小决定打包策略
        Path filePath;
        try {
            if (totalSize >= R18_ZIP_THRESHOLD) {
                log.info("图片总大小 {}MB 超过阈值，打包为ZIP。 PID: {}", String.format("%.2f", totalSize / (1024.0 * 1024.0)), pixivArtworkInfo.getPid());
                filePath = createZipArchive(files, FILE_OUTPUT_DIR, "pixiv_" + pixivArtworkInfo.getPid());
            } else {
                log.info("图片总大小 {}MB 未超过阈值，打包为DOCX/PDF。PID: {}", String.format("%.2f", totalSize / (1024.0 * 1024.0)), pixivArtworkInfo.getPid());
                filePath = pixivArtworkInfo.getType() == PixivArtworkType.GIF
                        ? DocxUtil.wrapImagesIntoDocx(files, FILE_OUTPUT_DIR)
                        : PdfUtil.wrapImagesIntoPdf(files, FILE_OUTPUT_DIR);
            }

            if (filePath == null) {
                log.error("生成R18文件包失败, pid={}", pixivArtworkInfo.getPid());
                bot.sendMsg(event, "生成R18文件包失败，请稍后重试。", false);
                return;
            }
        } catch (IOException e) {
            log.error("处理R18文件时发生IO异常, pid={}", pixivArtworkInfo.getPid(), e);
            bot.sendMsg(event, "处理文件时发生内部错误，请稍后重试。", false);
            return;
        }


        // 4. 上传文件并处理回调
        String fileName = filePath.getFileName().toString();
        CompletableFuture<SendMsgResult> sendFuture = BotUtils.uploadFileAsync(bot, event, filePath, fileName);

        sendFuture.whenCompleteAsync((result, throwable) -> {
            if (throwable != null) {
                log.error("上传文件 {} 失败", fileName, throwable);
                BotUtils.sendMsgByEvent(bot, event, "文件上传失败，可能是网络或权限问题。", false);
            } else if (result != null && result.isSuccess()) {
                log.info("文件 {} 上传成功，准备30秒后删除。", fileName);
                deleteGroupFileAfterDelay(bot, event, fileName);
            } else {
                log.warn("文件 {} 上传失败，返回结果: {}", fileName, result);
                BotUtils.sendMsgByEvent(bot, event, "文件上传失败，可能是奇怪的原因导致了。", false);
            }
            // 无论成功与否都删除本地临时文件
            FileUtil.deleteFileWithRetry(filePath.toAbsolutePath().toString());
        });
    }

    /**
     * 构建作品基本信息
     */
    private MsgUtils buildArtworkInfoMsg(PixivArtworkInfo detail, boolean includeDescription) {
        String description = includeDescription ? String.format("描述信息：%s\n", detail.getDescription()) : "";
        String text = String.format("""
                        作品标题：%s (%s)
                        作者：%s (%s)
                        %s作品链接：https://www.pixiv.net/artworks/%s
                        标签：%s
                        """, detail.getTitle(), detail.getPid(),
                detail.getUserName(), detail.getUid(),
                description,
                detail.getPid(),
                StringUtils.join(detail.getTags(), ','));
        return MsgUtils.builder().text(text);
    }

    /**
     * 延迟删除群文件
     */
    private void deleteGroupFileAfterDelay(Bot bot, AnyMessageEvent event, String fileName) {
        if (event.getGroupId() == null) return;
        try {
            CompletableFuture.delayedExecutor(30, TimeUnit.SECONDS).execute(() -> {
                BotUtils.deleteGroupFile(bot, event, fileName);
            });
        } catch (Exception e) {
            log.error("设置延迟删除群文件 {} 失败。", fileName, e);
        }
    }
    
    /**
     * 创建ZIP压缩包
     */
    private Path createZipArchive(List<File> filesToZip, String outputDir, String baseName) throws IOException {
        File dir = new File(outputDir);
        if (!dir.exists()) {
            dir.mkdirs();
        }
        
        String zipFileName = baseName + "_" + UUID.randomUUID().toString().substring(0, 8) + ".zip";
        Path zipFilePath = Paths.get(outputDir, zipFileName);

        log.info("开始创建ZIP文件: {}", zipFilePath);
        try (FileOutputStream fos = new FileOutputStream(zipFilePath.toFile());
             ZipArchiveOutputStream zaos = new ZipArchiveOutputStream(fos)) {
            for (File file : filesToZip) {
                if (file.exists() && file.isFile()) {
                    ZipArchiveEntry entry = new ZipArchiveEntry(file.getName());
                    zaos.putArchiveEntry(entry);
                    try (FileInputStream fis = new FileInputStream(file)) {
                        fis.transferTo(zaos);
                    }
                    zaos.closeArchiveEntry();
                }
            }
        }
        log.info("ZIP文件创建成功: {}", zipFilePath);
        return zipFilePath;
    }
}
