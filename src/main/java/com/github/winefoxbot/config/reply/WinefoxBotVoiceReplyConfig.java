package com.github.winefoxbot.config.reply;

import cn.hutool.core.io.FileUtil;
import jakarta.annotation.PreDestroy;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.monitor.FileAlterationListenerAdaptor;
import org.apache.commons.io.monitor.FileAlterationMonitor;
import org.apache.commons.io.monitor.FileAlterationObserver;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

/**
 * @author FlanChan (badapple495@outlook.com)
 * @since 2026-01-02-17:21
 */
@Configuration
@Data
@Import(WinefoxBotVoiceReplyProperties.class)
@Slf4j
public class WinefoxBotVoiceReplyConfig  implements InitializingBean {

    private final WinefoxBotVoiceReplyProperties voiceReplyProperties;

    // 文件监控器
    private FileAlterationMonitor monitor;

    @Override
    public void afterPropertiesSet() throws Exception {
        if (!voiceReplyProperties.getEnabled()) {
            return;
        }

        File voicesDir = new File(voiceReplyProperties.getVoiceRootDir());

        if (!voicesDir.exists()) {
            boolean mkdirs = voicesDir.mkdirs();
            if (mkdirs) {
                log.info("语音文件目录不存在，已创建: {}", voicesDir.getAbsolutePath());
            } else {
                log.error("语音文件目录不存在，且创建失败: {}", voicesDir.getAbsolutePath());
                return;
            }
        }


        // 1. 首次加载所有语音文件
        log.info("正在初始化并首次加载语音文件...");
        refreshVoices();

        // 监控间隔（例如5秒）
        long interval = 5000;
        FileAlterationObserver observer = new FileAlterationObserver(voiceReplyProperties.getVoiceRootPath().toFile());

        observer.addListener(new FileAlterationListenerAdaptor() {
            // 当任何文件或目录发生 创建、修改、删除 时，都重新加载所有语音

            @Override
            public void onFileDelete(File file) {
                log.debug("检测到 voices 目录变动: {}", file.getAbsolutePath());
                refreshVoices();
            }

            @Override
            public void onDirectoryChange(File directory) {
                log.debug("检测到 voices 目录变动: {}", directory.getAbsolutePath());
                refreshVoices();
            }

            @Override
            public void onDirectoryCreate(File directory) {
                log.debug("检测到 voices 目录变动: {}", directory.getAbsolutePath());
                refreshVoices();
            }

            @Override
            public void onDirectoryDelete(File directory) {
                log.debug("检测到 voices 目录变动: {}", directory.getAbsolutePath());
                refreshVoices();
            }

            @Override
            public void onFileCreate(File file) {
                log.debug("检测到 voices 目录变动: {}", file.getAbsolutePath());
                refreshVoices();
            }

            @Override
            public void onFileChange(File file) {
                log.debug("检测到 voices 目录变动: {}", file.getAbsolutePath());
                refreshVoices();
            }
        });

        monitor = new FileAlterationMonitor(interval, observer);
        monitor.start();
        log.info("语音文件监控服务已启动，监控目录: {}", voicesDir.getAbsolutePath());
    }
    // 语音文件映射，Key为分类，Value为该分类下的语音文件列表
    private final Map<String, List<File>> voiceMap = new ConcurrentHashMap<>();


    public synchronized void refreshVoices() {
        if (!voiceReplyProperties.getEnabled()) {
            log.warn("语音回复功能未启用，跳过刷新语音文件列表。");
            return;
        }

        log.debug("开始刷新语音文件列表...");
        Map<String, List<File>> newVoiceMap = new ConcurrentHashMap<>();
        try (Stream<Path> paths = Files.walk(voiceReplyProperties.getVoiceRootPath())) {
            paths.filter(Files::isRegularFile)
                    .forEach(filePath -> {
                        // 计算相对路径作为分类Key
                        Path relativePath = voiceReplyProperties.getVoiceRootPath().relativize(filePath.getParent());
                        // 兼容Windows和Linux的路径分隔符
                        String category = relativePath.toString().replace(File.separator, "/");

                        // 将文件添加到对应分类的列表中
                        newVoiceMap.computeIfAbsent(category, k -> new ArrayList<>()).add(filePath.toFile());
                    });

            voiceMap.clear();
            voiceMap.putAll(newVoiceMap);

            log.debug("语音文件列表刷新完成。加载了 {} 个分类。", voiceMap.size());
            if (log.isDebugEnabled()) {
                voiceMap.forEach((category, files) -> log.debug("分类 '{}': {} 个文件", category, files.size()));
            }

        } catch (Exception e) {
            log.error("刷新语音文件时发生错误", e);
        }
    }


    /**
     * Spring Bean 销毁前执行，优雅地停止监控
     */
    @PreDestroy
    private void shutdown() throws Exception {
        if (monitor != null) {
            log.info("正在停止文件监控服务...");
            monitor.stop();
            log.info("文件监控服务已停止。");
        }
    }
}