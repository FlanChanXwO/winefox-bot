package com.github.winefoxbot.core.utils;

import lombok.extern.slf4j.Slf4j;
import org.springframework.util.unit.DataSize;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.TimeUnit;

/**
 * @author FlanChan (badapple495@outlook.com)
 * @since 2025-12-10-3:26
 */
@Slf4j
public final class FileUtil {

    private FileUtil() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }

    public static String getFileUrlPrefix() {
        return System.getProperty("os.name").toLowerCase().contains("linux") ? "file://" : "file:///";
    }


    public static String formatDataSize(DataSize size) {
        long bytes = size.toBytes();

        if (bytes < 1024) {
            return bytes + " B";
        }
        double kb = bytes / 1024.0;
        if (kb < 1024) {
            return String.format("%.2f KB", kb);
        }
        double mb = kb / 1024.0;
        if (mb < 1024) {
            return String.format("%.2f MB", mb);
        }
        double gb = mb / 1024.0;
        return String.format("%.2f GB", gb);
    }

    /**
     * 带重试机制地删除文件。
     * <p>
     * 这个方法会尝试删除指定路径的文件。如果第一次删除失败（通常是因为文件被其他进程占用），
     * 它会等待一段指定的时间，然后再次尝试，直到达到最大重试次数。
     *
     * @param filePath    要删除的文件的完整路径。如果为 null 或空字符串，方法会直接返回。
     * @param maxRetries  最大重试次数。例如，设置为 5 表示总共会尝试 5 次。
     * @param delayMillis 每次重试之间的等待时间（以毫秒为单位）。
     * @return 如果文件最终被成功删除或文件原本就不存在，则返回 true；如果所有尝试都失败了，则返回 false。
     */
    public static boolean deleteFileWithRetry(String filePath, int maxRetries, long delayMillis) {
        if (filePath == null || filePath.trim().isEmpty()) {
            log.warn("文件路径为 null 或空，跳过删除操作。");
            return true; // 路径无效，视为“无需删除”，操作成功
        }

        Path path = Paths.get(filePath);

        // 如果文件一开始就不存在，直接返回成功
        if (!Files.exists(path)) {
            log.info("文件 '{}' 本身不存在，无需删除。", filePath);
            return true;
        }

        if (!Files.isWritable(path)) {
            log.error("文件不可读");
            return false;
        }


        for (int i = 0; i < maxRetries; i++) {
            try {
                Files.deleteIfExists(path);
                log.info("文件删除成功: {}", filePath);
                return true; // 删除成功，立即返回
            } catch (IOException e) {
                log.warn("第 {}/{} 次删除文件 '{}' 失败: {}", (i + 1), maxRetries, filePath, e.getMessage());
                if (i < maxRetries - 1) { // 如果不是最后一次尝试，则等待
                    try {
                        TimeUnit.MILLISECONDS.sleep(delayMillis);
                    } catch (InterruptedException ie) {
                        // 在异步回调等场景下，线程可能会被中断。
                        // 恢复中断状态是一种好习惯，并停止重试。
                        Thread.currentThread().interrupt();
                        log.error("删除文件的重试等待被中断，将停止重试。", ie);
                        break; // 停止重试循环
                    }
                }
            }
        }

        log.warn("在尝试 {} 次后，仍无法删除文件: {}", maxRetries, filePath);
        return false;
    }

    /**
     * 使用默认参数调用带重试的删除方法。
     * 默认尝试 5 次，每次间隔 2 秒。
     *
     * @param filePath 要删除的文件的路径。
     * @return 是否删除成功。
     */
    public static boolean deleteFileWithRetry(String filePath) {
        // 默认值可以根据你的业务场景进行调整
        return deleteFileWithRetry(filePath, 3, 1000);
    }
}