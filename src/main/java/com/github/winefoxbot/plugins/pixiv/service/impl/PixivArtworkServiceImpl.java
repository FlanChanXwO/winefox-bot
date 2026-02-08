package com.github.winefoxbot.plugins.pixiv.service.impl;

import cn.hutool.core.util.StrUtil;
import com.github.winefoxbot.core.context.BotContext;
import com.github.winefoxbot.core.exception.common.BusinessException;
import com.github.winefoxbot.core.model.dto.SendMsgResult;
import com.github.winefoxbot.core.utils.*;
import com.github.winefoxbot.plugins.pixiv.config.PixivPluginConfig;
import com.github.winefoxbot.plugins.pixiv.model.dto.common.PixivArtworkInfo;
import com.github.winefoxbot.plugins.pixiv.model.enums.ContentSendMode;
import com.github.winefoxbot.plugins.pixiv.service.PixivArtworkService;
import com.mikuac.shiro.common.utils.MsgUtils;
import com.mikuac.shiro.common.utils.ShiroUtils;
import com.mikuac.shiro.core.Bot;
import com.mikuac.shiro.dto.action.common.ActionData;
import com.mikuac.shiro.dto.action.common.MsgId;
import com.mikuac.shiro.dto.event.message.AnyMessageEvent;
import com.mikuac.shiro.dto.event.message.GroupMessageEvent;
import com.mikuac.shiro.dto.event.message.MessageEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
import org.apache.tomcat.util.buf.StringUtils;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * @author FlanChan
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class PixivArtworkServiceImpl implements PixivArtworkService {

    private final ImageObfuscator imageObfuscator;

    // R18 打包文件的临时输出目录
    private static final String FILE_OUTPUT_DIR = "data/files/pixiv/wrappers";
    
    private static final long ZIP_THRESHOLD = 100 * 1024 * 1024;

    /**
     * 统一处理并发送Pixiv作品的核心方法
     */
    @Override
    public void sendArtwork(PixivArtworkInfo info, List<File> files, String additionalText) {
        Bot bot = BotContext.CURRENT_BOT.get();
        AnyMessageEvent event = (AnyMessageEvent) BotContext.CURRENT_MESSAGE_EVENT.get();
        PixivPluginConfig config = (PixivPluginConfig) BotContext.CURRENT_PLUGIN_CONFIN.get();

        if (files == null || files.isEmpty()) {
            log.warn("PID: {} 的文件列表为空，无法发送。", info.getPid());
            SendMsgUtil.sendMsgByEvent(bot, event, "未能获取到 PID: " + info.getPid() + " 的图片文件！", false);
            return;
        }


        try {
            if (info.getIsR18()) {
                handleR18Artwork(info, files, config, bot, event);
            } else {
                handleNormalArtwork(info, files, additionalText, config, bot, event);
            }
        } catch (Exception e) {
            log.error("处理并发送 PID: {} 时发生未知错误。", info.getPid(), e);
            SendMsgUtil.sendMsgByEvent(bot, event, "处理作品时发生内部错误：" + e.getMessage(), false);
        }
    }


    /**
     * 处理非R18作品：根据配置选择发送方式
     */
    private void handleNormalArtwork(PixivArtworkInfo info, List<File> files, String additionalText, PixivPluginConfig config, Bot bot, AnyMessageEvent event) {
        List<Path> filePaths = files.stream().map(File::toPath).toList();
        ContentSendMode sendMode = (config != null && config.getSendMode() != null) 
                ? config.getSendMode() 
                : ContentSendMode.IMAGE;

        switch (sendMode) {
            case FORWARD -> sendForwardMessage(info, filePaths, additionalText, bot, event, config);
            case PDF -> sendPdfInternal(info, filePaths, bot, event, config);
            default -> sendImageMessage(info, filePaths, additionalText, bot, event, config);
        }
    }

    private void sendImageMessage(PixivArtworkInfo info, List<Path> filePaths, String additionalText, Bot bot, AnyMessageEvent event, PixivPluginConfig config) {
        // 1. 构建文本
        String text = (config != null && config.isSendArtworkInfo()) ? buildArtworkText(info, false) : "";
        if (StrUtil.isNotBlank(additionalText)) {
            text = StrUtil.isBlank(text) ? additionalText : text + "\n" + additionalText;
        }

        // 2. 转换文件路径为 URL
        List<String> imageUrls = filePaths.stream()
                .map(path -> FileUtil.getFileUrlPrefix() + path.toAbsolutePath())
                .toList();

        MsgUtils builder = MsgUtils.builder().text(text);
        imageUrls.forEach(builder::img);

        try {
            SendMsgResult result = SendMsgUtil.sendMsgByEvent(bot, event, builder.build(), false);
            if (result.isSuccess()) {
                log.info("Pixiv 常规作品发送成功: PID={}", info.getPid());
            } else {
                throw new RuntimeException("发送失败: " + result.getStatus());
            }
        } catch (Exception ex) {
            log.warn("Pixiv 直发失败，尝试混淆后重发: {}", ex.getMessage());
             // 混淆重试逻辑
            try {
                List<Path> obfuscatedPaths = imageObfuscator.wrap(filePaths);
                 if (obfuscatedPaths.isEmpty()) {
                    throw new BusinessException("图片混淆失败，无法重试");
                }
                List<String> newUrlList = obfuscatedPaths.stream()
                        .map(path -> FileUtil.getFileUrlPrefix() + path.toAbsolutePath())
                        .toList();

                MsgUtils retryBuilder = MsgUtils.builder().text(text);
                newUrlList.forEach(retryBuilder::img);

                SendMsgResult retryResult = SendMsgUtil.sendMsgByEvent(bot, event, retryBuilder.build(), false);
                if (retryResult.isSuccess()) {
                    log.info("Pixiv 混淆图片补发成功");
                } else {
                    throw new BusinessException("作品发送被拦截");
                }
            } catch (Exception e) {
                log.error("Pixiv 混淆重发异常", e);
                throw new BusinessException(e.getMessage());
            }
        }
    }

    private void sendForwardMessage(PixivArtworkInfo info, List<Path> filePaths, String additionalText, Bot bot, AnyMessageEvent event, PixivPluginConfig config) {
        try {
            List<String> msgList = new ArrayList<>();
            // 节点1: 信息
            String infoText = (config != null && config.isSendArtworkInfo()) ? buildArtworkText(info, true) : "";
             if (StrUtil.isNotBlank(additionalText)) {
                infoText = StrUtil.isBlank(infoText) ? additionalText : infoText + "\n" + additionalText;
            }
            if (StrUtil.isNotBlank(infoText)) {
                msgList.add(infoText);
            }

            // 节点2+: 图片
            for (Path path : filePaths) {
                String imgUrl = path.toUri().toString();
                msgList.add(MsgUtils.builder().img(imgUrl).build());
            }

            List<Map<String, Object>> forwardNodes = ShiroUtils.generateForwardMsg(bot, msgList);
            ActionData<MsgId> result = bot.sendForwardMsg(event, forwardNodes);
            if (result.getRetCode() != 0) {
                 throw new RuntimeException("合并转发发送失败");
            }
            log.info("Pixiv 合并转发成功: PID={}", info.getPid());
        } catch (Exception e) {
            log.warn("合并转发发送异常，尝试混淆后重发", e);
            try {
                // 混淆并重试转发
                List<Path> obfuscatedPaths = imageObfuscator.wrap(filePaths);
                if (obfuscatedPaths.isEmpty()) throw new RuntimeException("混淆失败");

                List<String> retryMsgList = new ArrayList<>();
                // 节点1: 信息 (保持不变)
                String infoText = (config != null && config.isSendArtworkInfo()) ? buildArtworkText(info, true) : "";
                if (StrUtil.isNotBlank(additionalText)) {
                    infoText = StrUtil.isBlank(infoText) ? additionalText : infoText + "\n" + additionalText;
                }
                if (StrUtil.isNotBlank(infoText)) {
                    retryMsgList.add(infoText);
                }

                // 节点2+: 混淆后的图片
                for (Path path : obfuscatedPaths) {
                    retryMsgList.add(MsgUtils.builder().img(path.toUri().toString()).build());
                }

                List<Map<String, Object>> retryNodes = ShiroUtils.generateForwardMsg(bot, retryMsgList);
                ActionData<MsgId> retryResult = bot.sendForwardMsg(event, retryNodes);
                if (retryResult.getRetCode() != 0) {
                    throw new RuntimeException("混淆后合并转发依然失败，RetCode=" + retryResult.getRetCode());
                }
                log.info("Pixiv 混淆合并转发发送成功");

            } catch (Exception ex) {
                log.error("混淆合并转发终极失败", ex);
                throw new BusinessException(e.getMessage());
            }
        }
    }

    private void sendPdfInternal(PixivArtworkInfo info, List<Path> filePaths, Bot bot, MessageEvent event, PixivPluginConfig config) {
         try {
            String baseName = "pixiv_" + info.getPid();
            Path packedFilePath = packFiles(filePaths, baseName);
            if (packedFilePath == null) throw new BusinessException("文件打包失败");
            
            String fileName = packedFilePath.getFileName().toString();
            FileUploadUtil.uploadFileAsync(bot, event, packedFilePath, fileName)
                .handle((result, throwable) -> {
                     try {
                        if (throwable != null) {
                            log.error("Pixiv PDF 发送失败", throwable);
                            SendMsgUtil.sendMsgByEvent(bot, event, "PDF文件发送失败: " + throwable.getMessage(), false);
                        } else {
                            String status = result != null ? result.getStatus() : "unknown";
                            log.info("Pixiv PDF 发送成功: PID={}, status={}", info.getPid(), status);
                            tryRevokeGroupFile(bot, event, fileName, config);
                        }
                    } finally {
                        FileUtil.deleteFileWithRetry(packedFilePath.toAbsolutePath().toString());
                    }
                    return null;
                });

         } catch (Exception e) {
            log.error("Pixiv PDF 打包发送异常", e);
            SendMsgUtil.sendMsgByEvent(bot, event, "PDF 发送异常", false);
         }
    }

    /**
     * 处理R18作品：发送文本详情 + 打包文件(自动撤回)
     */
    private void handleR18Artwork(PixivArtworkInfo info, List<File> files, PixivPluginConfig config, Bot bot, MessageEvent event) {
        // 1. 发送基本信息
        if (config != null && config.isSendArtworkInfo()) {
            String text = buildArtworkText(info, true);
            SendMsgUtil.sendMsgByEvent(bot, event, text, false);
        }

        // 2. 发送文件
        List<Path> filePaths = files.stream().map(File::toPath).toList();
        String baseName = "pixiv_" + info.getPid();

        try {
            Path packedFilePath = packFiles(filePaths, baseName);
             if (packedFilePath == null) throw new BusinessException("打包失败");

             String fileName = packedFilePath.getFileName().toString();
             FileUploadUtil.uploadFileAsync(bot, event, packedFilePath, fileName)
                .handle((result, throwable) -> {
                    try {
                        if (throwable != null) {
                            log.error("Pixiv R18 文件上传异常", throwable);
                            // 上传失败尝试降级混淆发送
                            sendObfuscatedImageMessage(info, filePaths, "R18文件上传失败，尝试混淆图片发送", bot, event, config);
                        } else if (result != null && result.isSuccess()) {
                            log.info("Pixiv R18 文件发送成功: PID={}", info.getPid());
                            tryRevokeGroupFile(bot, event, fileName, config);
                        } else {
                             log.warn("Pixiv R18 文件上传失败: {}", result);
                             sendObfuscatedImageMessage(info, filePaths, "R18文件上传未成功，尝试混淆图片发送", bot, event, config);
                        }
                    } finally {
                        FileUtil.deleteFileWithRetry(packedFilePath.toAbsolutePath().toString());
                    }
                    return null;
                });
        } catch (Exception e) {
            log.error("R18 文件发送流程失败", e);
            // 尝试混淆发送
            try {
                sendObfuscatedImageMessage(info, filePaths, "R18打包发送失败，尝试混淆图片发送", bot, event, config);
            } catch (Exception ex) {
                log.error("R18 混淆补发也失败了", ex);
                throw new BusinessException("真正的瑟图被吞了...");
            }
        }
    }

    private void sendObfuscatedImageMessage(PixivArtworkInfo info, List<Path> originalPaths, String text, Bot bot, MessageEvent event, PixivPluginConfig config) {
        try {
            List<Path> obfuscatedPaths = imageObfuscator.wrap(originalPaths);
            if (obfuscatedPaths.isEmpty()) return;

            String artworkInfo = (config != null && config.isSendArtworkInfo()) ? buildArtworkText(info, false) : "";
            String fullText = StrUtil.isBlank(artworkInfo) ? text : artworkInfo + "\n" + text;

            MsgUtils builder = MsgUtils.builder().text(fullText);

            for (Path p : obfuscatedPaths) {
                builder.img(p.toUri().toString());
            }

            SendMsgUtil.sendMsgByEvent(bot, event, builder.build(), false);
        } catch (Exception e) {
            log.error("混淆发送工具方法异常", e);
        }
    }

    private Path packFiles(List<Path> filePaths, String baseName) throws IOException {
        long totalSize = filePaths.stream().mapToLong(p -> p.toFile().length()).sum();
        File dir = new File(FILE_OUTPUT_DIR);
        if (!dir.exists() && !dir.mkdirs()) {
             throw new IOException("无法创建目录: " + dir.getAbsolutePath());
        }

        if (totalSize >= ZIP_THRESHOLD) {
            String zipName = baseName + "_" + UUID.randomUUID().toString().substring(0, 8) + ".zip";
            Path zipPath = Paths.get(FILE_OUTPUT_DIR, zipName);
            createZip(filePaths, zipPath);
            return zipPath;
        } else {
            return PdfUtil.wrapImageIntoPdf(filePaths, FILE_OUTPUT_DIR + File.separator + baseName);
        }
    }

    private void createZip(List<Path> files, Path zipFilePath) throws IOException {
        try (FileOutputStream fos = new FileOutputStream(zipFilePath.toFile());
             ZipArchiveOutputStream zaos = new ZipArchiveOutputStream(fos)) {
            for (Path path : files) {
                File file = path.toFile();
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
    }

     private void tryRevokeGroupFile(Bot bot, MessageEvent event, String fileName, PixivPluginConfig config) {
        Long groupId = null;
        if (event instanceof GroupMessageEvent ge) {
            groupId = ge.getGroupId();
        }
        if (groupId == null) return;

        boolean shouldRevoke = (config != null) && config.isRevokeEnabled();
        if (!shouldRevoke) return;

        int delay = config.getRevokeDelay();

        log.info("将在 {} 秒后撤回群组 {} 的文件: {}", delay, groupId, fileName);

        Long finalGroupId = groupId;
        CompletableFuture.delayedExecutor(delay, TimeUnit.SECONDS).execute(() -> {
            try {
                GroupMessageEvent ge = (GroupMessageEvent) event;
                FileUploadUtil.deleteGroupFile(bot, ge, fileName);
                log.info("群组 {} 已撤回文件: {}", finalGroupId, fileName);
            } catch (Exception e) {
                log.error("自动撤回群文件失败: {}", fileName, e);
            }
        });
    }

    /**
     * 构建作品文本信息
     */
    private String buildArtworkText(PixivArtworkInfo detail, boolean includeDescription) {
        String description = includeDescription && StrUtil.isNotBlank(detail.getDescription())
                ? String.format("描述信息：%s\n", detail.getDescription())
                : "";

        return String.format("""
                        作品标题：%s (%s)
                        作者：%s (%s)
                        %s作品链接：https://www.pixiv.net/artworks/%s
                        标签：%s
                        """,
                detail.getTitle(), detail.getPid(),
                detail.getUserName(), detail.getUid(),
                description,
                detail.getPid(),
                StringUtils.join(detail.getTags(), ',')
        );
    }
}
