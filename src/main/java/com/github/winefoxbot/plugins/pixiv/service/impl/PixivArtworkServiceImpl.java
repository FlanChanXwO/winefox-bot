package com.github.winefoxbot.plugins.pixiv.service.impl;

import cn.hutool.core.util.StrUtil;
import com.github.winefoxbot.core.service.shiro.ShiroSafeSendMessageService;
import com.github.winefoxbot.core.utils.FileUtil;
import com.github.winefoxbot.plugins.pixiv.model.dto.common.PixivArtworkInfo;
import com.github.winefoxbot.plugins.pixiv.service.PixivArtworkService;
import com.mikuac.shiro.core.Bot;
import com.mikuac.shiro.dto.event.message.AnyMessageEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.tomcat.util.buf.StringUtils;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.File;
import java.nio.file.Path;
import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class PixivArtworkServiceImpl implements PixivArtworkService {

    private final ShiroSafeSendMessageService safeSendMessageService;

    // R18 打包文件的临时输出目录
    private static final String FILE_OUTPUT_DIR = "data/files/pixiv/wrappers";

    /**
     * 统一处理并发送Pixiv作品的核心方法
     * 现在的实现非常轻量级，核心逻辑已下沉至 ShiroSafeSendMessageService
     */
    @Override
    @Async("taskExecutor")
    public void sendArtwork(Bot bot, AnyMessageEvent event, PixivArtworkInfo pixivArtworkInfo, List<File> files, String additionalText) {
        processArtworkSending(pixivArtworkInfo, files, additionalText);
    }

    @Override
    public void sendArtworkToUser(Bot bot, Long userId, PixivArtworkInfo pixivArtworkInfo, List<File> files, String additionalText) {
        processArtworkSending(pixivArtworkInfo, files, additionalText);
    }

    @Override
    public void sendArtworkToGroup(Bot bot, Long groupId, PixivArtworkInfo pixivArtworkInfo, List<File> files, String additionalText) {
        processArtworkSending(pixivArtworkInfo, files, additionalText);
    }

    /**
     * 内部通用处理逻辑
     * 不再需要传递 Bot 和 Event，SafeService 会自动从 Context 获取
     */
    private void processArtworkSending(PixivArtworkInfo info, List<File> files, String additionalText) {
        if (files == null || files.isEmpty()) {
            log.warn("PID: {} 的文件列表为空，无法发送。", info.getPid());
            safeSendMessageService.sendMessage("未能获取到 PID: " + info.getPid() + " 的图片文件！", null, null, null);
            return;
        }

        try {
            if (info.getIsR18()) {
                handleR18Artwork(info, files);
            } else {
                handleNormalArtwork(info, files, additionalText);
            }
        } catch (Exception e) {
            log.error("处理并发送 PID: {} 时发生未知错误。", info.getPid(), e);
            safeSendMessageService.sendMessage("处理作品时发生内部错误：" + e.getMessage(), null, null, null);
        }
    }

    /**
     * 处理非R18作品：直接发送图文消息
     */
    private void handleNormalArtwork(PixivArtworkInfo info, List<File> files, String additionalText) {
        // 1. 构建文本
        String text = buildArtworkText(info, false);
        if (StrUtil.isNotBlank(additionalText)) {
            text += "\n" + additionalText;
        }

        // 2. 转换文件路径为 URL (file://...)
        List<String> imageUrls = files.stream()
                .map(file -> FileUtil.getFileUrlPrefix() + file.getAbsolutePath())
                .toList();

        // 3. 调用安全发送服务
        safeSendMessageService.sendMessage(
                text,
                imageUrls,
                result -> log.info("Pixiv 常规作品发送成功: PID={}", info.getPid()),
                ex -> log.error("Pixiv 常规作品发送失败: PID={}", info.getPid(), ex)
        );
    }

    /**
     * 处理R18作品：发送文本详情 + 打包文件(自动撤回)
     */
    private void handleR18Artwork(PixivArtworkInfo info, List<File> files) {
        // 1. 先发送作品的基本信息文本
        String text = buildArtworkText(info, true);
        safeSendMessageService.sendMessage(text, null, null, null);

        // 2. 发送安全文件 (PDF/ZIP)
        // ShiroSafeSendMessageService 会自动处理：
        // - 大小阈值判断 (ZIP vs PDF)
        // - 文件上传
        // - 失败处理
        // - 配置读取与自动撤回 (Auto Revoke)
        List<Path> filePaths = files.stream().map(File::toPath).toList();
        String baseName = "pixiv_" + info.getPid();

        safeSendMessageService.sendSafeFiles(
                filePaths,
                FILE_OUTPUT_DIR,
                baseName,
                result -> log.info("Pixiv R18文件发送成功: PID={}", info.getPid()),
                (ex, path) -> log.error("Pixiv R18文件发送失败: PID={}", info.getPid(), ex)
        );
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
