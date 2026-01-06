package com.github.winefoxbot.core.utils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;

/**
 * 资源加载工具类
 * 支持 "classpath:" 前缀加载类路径资源
 * 否则尝试加载文件系统资源
 */
public class ResourceLoader {

    public static final String CLASSPATH_PREFIX = "classpath:";

    /**
     * 获取资源流
     * @param location 资源路径 (例如: "classpath:config/app.properties" 或 "/var/data/file.txt")
     * @return InputStream 输入流
     * @throws IOException 如果找不到文件或读取失败
     */
    public static InputStream getInputStream(String location) throws IOException {
        if (location == null || location.trim().isEmpty()) {
            throw new IllegalArgumentException("Resource location must not be null or empty");
        }

        if (location.startsWith(CLASSPATH_PREFIX)) {
            return loadFromClassPath(location);
        } else {
            return loadFromFileSystem(location);
        }
    }

    private static InputStream loadFromClassPath(String location) throws FileNotFoundException {
        // 去掉 "classpath:" 前缀
        String path = location.substring(CLASSPATH_PREFIX.length());
        
        // 处理可能存在的开头斜杠 (ClassLoader.getResource 不推荐开头带 /)
        if (path.startsWith("/")) {
            path = path.substring(1);
        }

        // 优先使用当前线程的 ClassLoader，防止在某些容器中加载不到
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        if (classLoader == null) {
            classLoader = ResourceLoader.class.getClassLoader();
        }

        InputStream stream = classLoader.getResourceAsStream(path);
        if (stream == null) {
            throw new FileNotFoundException("Resource not found in classpath: " + path);
        }
        return stream;
    }

    private static InputStream loadFromFileSystem(String location) throws FileNotFoundException {
        File file = new File(location);
        if (!file.exists()) {
            // 尝试作为一个 URL 处理 (例如 file:///...)，或者是相对路径
             throw new FileNotFoundException("Resource not found in file system: " + location);
        }
        return new FileInputStream(file);
    }
}