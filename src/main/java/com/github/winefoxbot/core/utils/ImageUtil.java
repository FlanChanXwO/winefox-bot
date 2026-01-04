package com.github.winefoxbot.core.utils;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * @author FlanChan (badapple495@outlook.com)
 * @since 2026-01-02-0:26
 */
 public final class ImageUtil {
     private ImageUtil() {
         throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
     }
    /**
     * 检测文件是否为GIF。
     * @param file 待检测文件
     * @return 如果是GIF则返回true
     */
    public static boolean isGifFile(File file) {
        try (InputStream is = new FileInputStream(file)) {
            byte[] header = new byte[3];
            if (is.read(header) != 3) {
                return false;
            }
            String magic = new String(header);
            return "GIF".equals(magic);
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }

}