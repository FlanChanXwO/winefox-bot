package com.github.winefoxbot.core.utils;

import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;

import static com.github.winefoxbot.core.constants.AppConstants.OUTTERNAL_ROOT;

/**
 * 动态资源加载器
 * 支持优先从外部文件系统加载资源，找不到时回退到 classpath 加载
 * 适用于生产环境和开发环境的资源管理
 *
 * @author FlanChan
 * @since 2024-06-15
 */
public class DynamicResourceLoader {

    // 定义外部资源根目录
    private static final String EXTERNAL_ROOT =  OUTTERNAL_ROOT + "/";

    /**
     * 智能获取资源流
     * 优先查找运行目录下的 resources/ 文件夹，如果没有，再找 classpath
     *
     * @param relativePath 相对路径，例如 "templates/winefox_daily_report/res/css/style.css"
     *                     (注意：不要带 classpath: 或 file: 前缀)
     */
    public static InputStream getInputStream(String relativePath) throws IOException {
        // 1. 去掉可能存在的 classpath: 前缀，统一成相对路径
        String cleanPath = relativePath.replace("classpath:", "").replace("file:", "");
        if (cleanPath.startsWith("/")) {
            cleanPath = cleanPath.substring(1);
        }

        // 2. 尝试从文件系统加载 (生产环境优先)
        Path externalPath = Paths.get(EXTERNAL_ROOT, cleanPath);
        if (Files.exists(externalPath)) {
            // log.debug("Loading external resource: {}", externalPath);
            return Files.newInputStream(externalPath);
        }

        // 3. 回退到 Classpath 加载 (开发环境)
        // log.debug("Loading classpath resource: {}", cleanPath);
        ClassPathResource cpResource = new ClassPathResource(cleanPath);
        if (!cpResource.exists()) {
            throw new IOException("Resource not found in FileSystem or Classpath: " + cleanPath);
        }
        return cpResource.getInputStream();
    }

    /**
     * 读取资源并转为 Base64 字符串 (图片专用)
     */
    public static String getResourceAsBase64(String path) {
        try (InputStream is = getInputStream(path)) {
            byte[] bytes = StreamUtils.copyToByteArray(is);
            // 简单的 MIME 类型推断，实际可根据后缀判断
            String mimeType = "image/png"; 
            if (path.endsWith(".jpg") || path.endsWith(".jpeg")) mimeType = "image/jpeg";
            else if (path.endsWith(".css")) mimeType = "text/css";
            
            return "data:" + mimeType + ";base64," + Base64.getEncoder().encodeToString(bytes);
        } catch (IOException e) {
            throw new RuntimeException("Failed to load resource: " + path, e);
        }
    }
    
    /**
     * 读取文本内容 (CSS/HTML专用)
     */
    public static String getResourceAsString(String path) {
        try (InputStream is = getInputStream(path)) {
            return StreamUtils.copyToString(is, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException("Failed to read resource: " + path, e);
        }
    }

    public static Path getPath(String path) {
        // 1. 尝试从文件系统加载 (生产环境优先)
        Path externalPath = Paths.get(EXTERNAL_ROOT, path);
        if (Files.exists(externalPath)) {
            return externalPath;
        }

        // 2. 回退到 Classpath 加载 (开发环境)
        try {
            FileSystemResource fsResource = new FileSystemResource(new ClassPathResource(path).getFile());
            return fsResource.getFile().toPath();
        } catch (IOException e) {
            throw new RuntimeException("Resource not found in FileSystem or Classpath: " + path, e);
        }
    }
}
