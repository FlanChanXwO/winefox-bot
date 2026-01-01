package com.github.winefoxbot.utils;

import org.springframework.util.unit.DataSize;

/**
 * @author FlanChan (badapple495@outlook.com)
 * @since 2025-12-10-3:26
 */
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

}