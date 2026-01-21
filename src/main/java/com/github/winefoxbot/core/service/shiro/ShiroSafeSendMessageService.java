package com.github.winefoxbot.core.service.shiro;

import com.github.winefoxbot.core.model.dto.SendMsgResult;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Shiro 安全消息发送服务接口
 * 利用 BotContext 获取上下文，处理通用的消息发送、文件包装、自动撤回和错误回调
 *
 * @author FlanChan (badapple495@outlook.com)
 * @since 2026-01-19-11:29
 */
public interface ShiroSafeSendMessageService {

    /**
     * 发送普通消息（文本 + 图片列表）
     * 包含重试机制和回调处理
     *
     * @param text      文本内容 (可为 null)
     * @param imageUrls 图片 URL 列表 (可为 null)
     * @param onSuccess 发送成功回调
     * @param onFailure 发送失败回调 (传入异常)
     */
    void sendMessage(String text,
                                        List<String> imageUrls,
                                        Consumer<SendMsgResult> onSuccess,
                                        Consumer<Throwable> onFailure);

    /**
     * 安全发送一组文件（自动判断打包方式：PDF/Zip）
     * 适用于 R18 内容或多图合集，包含自动撤回和本地清理逻辑
     *
     * @param filePaths    文件路径列表
     * @param outputDir    临时文件输出目录
     * @param fileBaseName 生成文件的基础名称
     * @param onSuccess    发送成功回调 (传入 SendMsgResult)
     * @param onFailure    发送失败回调 (传入异常和生成的临时文件路径，以便上层决定是否保留或重试)
     */
    void sendSafeFiles(List<Path> filePaths,
                                          String outputDir,
                                          String fileBaseName,
                                          Consumer<SendMsgResult> onSuccess,
                                          BiConsumer<Throwable, Path> onFailure);
}
