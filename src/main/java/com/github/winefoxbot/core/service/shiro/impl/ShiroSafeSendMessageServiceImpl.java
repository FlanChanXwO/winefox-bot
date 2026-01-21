package com.github.winefoxbot.core.service.shiro.impl;

import com.github.winefoxbot.core.context.BotContext;
import com.github.winefoxbot.core.manager.ConfigManager;
import com.github.winefoxbot.core.model.dto.SendMsgResult;
import com.github.winefoxbot.core.plugins.adultmanage.config.AdultContentConfig;
import com.github.winefoxbot.core.service.shiro.ShiroSafeSendMessageService;
import com.github.winefoxbot.core.utils.*;
import com.mikuac.shiro.common.utils.MsgUtils;
import com.mikuac.shiro.core.Bot;
import com.mikuac.shiro.dto.event.message.AnyMessageEvent;
import com.mikuac.shiro.dto.event.message.GroupMessageEvent;
import com.mikuac.shiro.dto.event.message.MessageEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
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
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Shiro 安全消息发送服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ShiroSafeSendMessageServiceImpl implements ShiroSafeSendMessageService {

    private final ConfigManager configManager;

    private static final String KEY_REVOKE_ENABLED = ConfigReflectionUtil.getFullKey(AdultContentConfig.class, "revokeEnabled");
    private static final String KEY_REVOKE_DELAY = ConfigReflectionUtil.getFullKey(AdultContentConfig.class, "revokeDelay");


    // 默认值
    private static final boolean DEFAULT_REVOKE_ENABLED = false;
    private static final int DEFAULT_REVOKE_DELAY = 30;

    // 100MB 阈值
    private static final long ZIP_THRESHOLD = 100 * 1024 * 1024;

    @Override
    public void sendMessage(String text, List<String> imageUrls, Consumer<SendMsgResult> onSuccess, Consumer<Throwable> onFailure) {
        try {
            Bot bot = getBotFromContext();
            MessageEvent event = getEventFromContext();

            MsgUtils builder = MsgUtils.builder();
            if (text != null && !text.isEmpty()) {
                builder.text(text);
            }
            if (imageUrls != null) {
                for (String url : imageUrls) {
                    builder.img(url);
                }
            }

            SendMsgResult result = SendMsgUtil.sendMsgByEvent(bot, event, builder.build(), false);

            if (result != null && result.isSuccess()) {
                if (onSuccess != null) onSuccess.accept(result);
            } else {
                String errorMsg = result != null ? result.getStatus() : "Unknown Error";
                if (onFailure != null) onFailure.accept(new RuntimeException("消息发送失败: " + errorMsg));
            }
        } catch (Exception e) {
            log.error("发送消息时发生未捕获异常", e);
            if (onFailure != null) onFailure.accept(e);
        }
    }

    @Override
    public void sendSafeFiles(List<Path> filePaths, String outputDir, String fileBaseName, Consumer<SendMsgResult> onSuccess, BiConsumer<Throwable, Path> onFailure) {
        Bot bot;
        MessageEvent event;
        try {
            bot = getBotFromContext();
            event = getEventFromContext();
        } catch (IllegalStateException e) {
            if (onFailure != null) onFailure.accept(e, null);
            return;
        }

        if (filePaths == null || filePaths.isEmpty()) {
            if (onFailure != null) onFailure.accept(new IllegalArgumentException("文件列表为空"), null);
            return;
        }

        Path packedFilePath;
        try {
            packedFilePath = packFiles(filePaths, outputDir, fileBaseName);
            if (packedFilePath == null) {
                throw new IOException("打包文件生成失败");
            }
        } catch (Exception e) {
            log.error("文件打包失败: {}", fileBaseName, e);
            if (onFailure != null) onFailure.accept(e, null);
            return;
        }

        String fileName = packedFilePath.getFileName().toString();
        log.info("准备发送打包文件: {}", packedFilePath);

        FileUploadUtil.uploadFileAsync(bot, event, packedFilePath, fileName)
                .handle((result, throwable) -> {
                    try {
                        if (throwable != null) {
                            log.error("文件上传异常: {}", fileName, throwable);
                            if (onFailure != null) onFailure.accept(throwable, packedFilePath);
                        } else if (result != null && result.isSuccess()) {
                            if (onSuccess != null) onSuccess.accept(result);

                            // *** 触发撤回逻辑 ***
                            tryRevokeGroupFile(bot, event, fileName);

                        } else {
                            log.warn("文件上传未成功: {}", result);
                            if (onFailure != null) onFailure.accept(new RuntimeException("文件上传返回失败状态: " + (result != null ? result.getStatus() : "null")), packedFilePath);
                        }
                    } finally {
                        FileUtil.deleteFileWithRetry(packedFilePath.toAbsolutePath().toString());
                    }
                    return null;
                });
    }

    // --- 核心逻辑：撤回控制 ---

    /**
     * 尝试撤回群文件
     */
    private void tryRevokeGroupFile(Bot bot, MessageEvent event, String fileName) {
        Long groupId = getSafeGroupId(event);
        Long userId = getSafeUserId(event);

        // 如果没有 groupId，说明是私聊，不需要撤回
        if (groupId == null) {
            log.debug("当前为私聊环境，跳过文件撤回逻辑。");
            return;
        }

        // 直接向 ConfigManager 查询特定的 Key
        // 这样无论当前 BotContext 里是 WaterGroupConfig 还是其他什么，
        // 我们都会强制读取 setu 插件设定的规则。
        boolean shouldRevoke = configManager.getBoolean(
                KEY_REVOKE_ENABLED, // "setu.revoke.enabled"
                userId,
                groupId,
                DEFAULT_REVOKE_ENABLED
        );

        if (!shouldRevoke) {
            log.debug("群组 {} 未开启文件自动撤回 (key={})", groupId, KEY_REVOKE_ENABLED);
            return;
        }

        int delay = configManager.getInt(
                KEY_REVOKE_DELAY,   // "setu.revoke.delay"
                userId,
                groupId,
                DEFAULT_REVOKE_DELAY
        );

        log.info("将在 {} 秒后撤回群组 {} 的文件: {}", delay, groupId, fileName);

        CompletableFuture.delayedExecutor(delay, TimeUnit.SECONDS).execute(() -> {
            try {
                performRevoke(bot, event, fileName, groupId);
            } catch (Exception e) {
                log.error("自动撤回群文件失败: {}", fileName, e);
            }
        });
    }

    private void performRevoke(Bot bot, MessageEvent event, String fileName, Long groupId) {
        if (event instanceof GroupMessageEvent ge) {
            FileUploadUtil.deleteGroupFile(bot, ge, fileName);
            log.info("群组 {} 已撤回文件: {}", groupId, fileName);
        }
    }


    // --- 辅助方法 ---

    private Bot getBotFromContext() {
        if (!BotContext.CURRENT_BOT.isBound()) {
            throw new IllegalStateException("当前线程没有绑定 Bot 上下文");
        }
        return BotContext.CURRENT_BOT.get();
    }

    private MessageEvent getEventFromContext() {
        if (!BotContext.CURRENT_MESSAGE_EVENT.isBound()) {
            throw new IllegalStateException("当前线程没有绑定 Event 上下文");
        }
        return BotContext.CURRENT_MESSAGE_EVENT.get();
    }

    private Long getSafeGroupId(MessageEvent event) {
        if (event instanceof GroupMessageEvent ge) {
            return ge.getGroupId();
        }
        return null;
    }

    private Long getSafeUserId(MessageEvent event) {
        try {
            return event.getUserId();
        } catch (Exception e) {
            return null;
        }
    }

    private Path packFiles(List<Path> filePaths, String outputDir, String baseName) throws IOException {
        long totalSize = filePaths.stream().mapToLong(p -> p.toFile().length()).sum();
        File dir = new File(outputDir);
        if (!dir.exists()) {
            dir.mkdirs();
        }

        if (totalSize >= ZIP_THRESHOLD) {
            String zipName = baseName + "_" + UUID.randomUUID().toString().substring(0, 8) + ".zip";
            Path zipPath = Paths.get(outputDir, zipName);
            createZip(filePaths, zipPath);
            return zipPath;
        } else {
            return PdfUtil.wrapImageIntoPdf(filePaths, outputDir + File.separator + baseName);
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
}
