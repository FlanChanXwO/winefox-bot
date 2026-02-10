package com.github.winefoxbot.plugins.pixiv.service.impl;

import cn.hutool.core.util.StrUtil;
import com.github.winefoxbot.core.context.BotContext;
import com.github.winefoxbot.core.exception.common.BusinessException;
import com.github.winefoxbot.core.model.dto.SendMsgResult;
import com.github.winefoxbot.core.util.*;
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
import org.apache.tomcat.util.buf.StringUtils;
import org.springframework.stereotype.Service;

import java.io.File;
import java.net.SocketTimeoutException;
import java.nio.file.Path;
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
    
    // private static final long ZIP_THRESHOLD = 100 * 1024 * 1024; // This is no longer needed as packFiles is removed

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
            case PDF_OR_WORD -> sendDocumentInternal(info, files, bot, event, config);
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
            if (ex instanceof SocketTimeoutException || ex.getCause() instanceof SocketTimeoutException) {
                log.warn("Pixiv 直发超时，可能已发送成功，不再重试: {}", ex.getMessage());
                return;
            }
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
            if (e instanceof SocketTimeoutException || e.getCause() instanceof SocketTimeoutException) {
                log.warn("合并转发发送超时，可能已发送成功，不再重试: {}", e.getMessage());
                return;
            }
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

    private void sendDocumentInternal(PixivArtworkInfo info, List<File> files, Bot bot, MessageEvent event, PixivPluginConfig config) {
         try {
            String baseName = "pixiv_" + info.getPid();
            Path packedFilePath;
            String sendModeName;

            boolean isGif = files.stream().anyMatch(file -> file.getName().toLowerCase().endsWith(".gif"));

            if (isGif) {
                String fileName = baseName + ".docx";
                packedFilePath = DocxUtil.wrapImagesIntoDocx(files, FILE_OUTPUT_DIR, fileName);
                sendModeName = "Word";
            } else {
                List<Path> filePaths = files.stream().map(File::toPath).toList();
                packedFilePath = PdfUtil.wrapImageIntoPdf(filePaths, FILE_OUTPUT_DIR + File.separator + baseName);
                sendModeName = "PDF";
            }

            if (packedFilePath == null) throw new BusinessException("文件打包失败");
            
            String fileName = packedFilePath.getFileName().toString();
            FileUploadUtil.uploadFileAsync(bot, event, packedFilePath, fileName)
                .handle((result, throwable) -> {
                     try {
                         if (result != null && result.isSuccess()) {
                             String status = result.getStatus();
                             log.info("Pixiv {} 发送成功: PID={}, status={}", sendModeName, info.getPid(), status);
                             tryRevokeGroupFile(bot, event, fileName, config);
                        } else {
                             log.error("Pixiv {} 发送失败", sendModeName, throwable);
                             SendMsgUtil.sendMsgByEvent(bot, event, sendModeName + "文件发送失败: " + throwable.getMessage(), false);
                        }
                    } finally {
                        FileUtil.deleteFileWithRetry(packedFilePath.toAbsolutePath().toString());
                    }
                    return null;
                });

         } catch (Exception e) {
            log.error("Pixiv 文档打包发送异常", e);
            SendMsgUtil.sendMsgByEvent(bot, event, "文档发送异常", false);
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
        String baseName = "pixiv_" + info.getPid();

        try {
            String fileName = baseName + ".docx";
            Path packedFilePath = DocxUtil.wrapImagesIntoDocx(files, FILE_OUTPUT_DIR, fileName);
             if (packedFilePath == null) throw new BusinessException("打包失败");

             FileUploadUtil.uploadFileAsync(bot, event, packedFilePath, fileName)
                .handle((result, _) -> {
                    try {
                        if (result != null && result.isSuccess()) {
                            log.info("Pixiv R18 文件发送成功: PID={}", info.getPid());
                            tryRevokeGroupFile(bot, event, fileName, config);
                        } else {
                            log.warn("Pixiv R18 文件上传失败: {}", result);
                        }
                    } finally {
                        FileUtil.deleteFileWithRetry(packedFilePath.toAbsolutePath().toString());
                    }
                    return null;
                });
        } catch (Exception e) {
            log.error("R18 文件发送流程失败", e);
            throw new BusinessException("R18文件发送失败...");
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
