package com.github.winefoxbot.core.utils;

import org.apache.tika.Tika;
import org.springframework.core.io.Resource;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Base64;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Base64 工具类，支持多种数据源与带 MIME-Type 前缀的 Base64 字符串之间的转换。
 * @author FlanChan
 */
public final class Base64Utils {

    private static final Tika TIKA_INSTANCE = new Tika();
    private static final Pattern BASE64_PREFIX_PATTERN = Pattern.compile("^data:(.*?);base64,");

    private Base64Utils() {
        // 防止实例化
        throw new UnsupportedOperationException("Base64Utils is a utility class and cannot be instantiated");
    }


    /**
     * 将 Spring Resource 转换为带有 data-uri 前缀的 Base64 字符串。
     *
     * @param resource Spring 资源对象, 不可为 null
     * @return 格式为 "data:[MIME-TYPE];base64,..." 的 Base64 字符串
     * @throws IOException 如果读取资源失败
     */
    public static String toBase64String(Resource resource) throws IOException {
        Objects.requireNonNull(resource, "Resource must not be null");
        try (InputStream inputStream = resource.getInputStream()) {
            byte[] bytes = inputStream.readAllBytes();
            String mimeType = TIKA_INSTANCE.detect(bytes, resource.getFilename());
            return toBase64String(bytes, mimeType);
        }
    }

    /**
     * 将文件转换为带有 data-uri 前缀的 Base64 字符串。
     *
     * @param file 文件对象, 不可为 null
     * @return 格式为 "data:[MIME-TYPE];base64,..." 的 Base64 字符串
     * @throws IOException 如果读取文件失败
     */
    public static String toBase64String(File file) throws IOException {
        Objects.requireNonNull(file, "File must not be null");
        byte[] bytes = Files.readAllBytes(file.toPath());
        String mimeType = TIKA_INSTANCE.detect(bytes, file.getName());
        return toBase64String(bytes, mimeType);
    }

    /**
     * 将 Path 对象指向的文件转换为带有 data-uri 前缀的 Base64 字符串。
     *
     * @param path 文件路径对象, 不可为 null
     * @return 格式为 "data:[MIME-TYPE];base64,..." 的 Base64 字符串
     * @throws IOException 如果读取文件失败
     */
    public static String toBase64String(Path path) throws IOException {
        Objects.requireNonNull(path, "Path must not be null");
        return toBase64String(path.toFile());
    }

    /**
     * 将网络 URL 指向的资源转换为带有 data-uri 前缀的 Base64 字符串。
     *
     * @param urlString 图片/文件 URL 链接, 不可为 null
     * @return 格式为 "data:[MIME-TYPE];base64,..." 的 Base64 字符串
     * @throws IOException 如果下载或读取失败
     */
    public static String toBase64String(String urlString) throws IOException {
        Objects.requireNonNull(urlString, "URL string must not be null");
        URL url = URI.create(urlString).toURL();
        try (InputStream inputStream = url.openStream()) {
            byte[] bytes = inputStream.readAllBytes();
            String mimeType = TIKA_INSTANCE.detect(bytes, urlString);
            return toBase64String(bytes, mimeType);
        }
    }

    /**
     * 将字节数组转换为带有 data-uri 前缀的 Base64 字符串。
     *
     * @param data 字节数组, 不可为 null
     * @return 格式为 "data:[MIME-TYPE];base64,..." 的 Base64 字符串
     */
    public static String toBase64String(byte[] data) {
        Objects.requireNonNull(data, "Byte array must not be null");
        String mimeType = TIKA_INSTANCE.detect(data);
        return toBase64String(data, mimeType);
    }
    
    /**
     * 将字节数组和指定的MIME类型转换为带有 data-uri 前缀的 Base64 字符串。
     *
     * @param data     字节数组, 不可为 null
     * @param mimeType MIME 类型, 例如 "image/jpeg"
     * @return 格式为 "data:[MIME-TYPE];base64,..." 的 Base64 字符串
     */
    public static String toBase64String(byte[] data, String mimeType) {
        Objects.requireNonNull(data, "Byte array must not be null");
        String encoded = Base64.getEncoder().encodeToString(data);
        return String.format("data:%s;base64,%s", mimeType, encoded);
    }


    // --- 从 Base64 字符串转换 ---

    /**
     * 将带有 data-uri 前缀的 Base64 字符串解码为字节数组。
     *
     * @param base64String 格式为 "data:[MIME-TYPE];base64,..." 的字符串
     * @return 解码后的字节数组
     * @throws IllegalArgumentException 如果字符串格式不正确
     */
    public static byte[] toByteArray(String base64String) {
        Objects.requireNonNull(base64String, "Base64 string must not be null");
        String encodedPart = extractBase64Data(base64String);
        return Base64.getDecoder().decode(encodedPart);
    }

    /**
     * 将带有 data-uri 前缀的 Base64 字符串解码并保存到指定文件。
     * 如果文件已存在，它将被覆盖。
     *
     * @param base64String 格式为 "data:[MIME-TYPE];base64,..." 的字符串
     * @param outputFile   目标文件
     * @throws IOException              如果写入文件失败
     * @throws IllegalArgumentException 如果字符串格式不正确
     */
    public static void toFile(String base64String, File outputFile) throws IOException {
        Objects.requireNonNull(outputFile, "Output file must not be null");
        byte[] decodedBytes = toByteArray(base64String);
        Files.write(outputFile.toPath(), decodedBytes, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
    }

    /**
     * 将带有 data-uri 前缀的 Base64 字符串解码并保存到指定路径。
     * 如果文件已存在，它将被覆盖。
     *
     * @param base64String 格式为 "data:[MIME-TYPE];base64,..." 的字符串
     * @param outputPath   目标文件路径
     * @throws IOException              如果写入文件失败
     * @throws IllegalArgumentException 如果字符串格式不正确
     */
    public static void toFile(String base64String, Path outputPath) throws IOException {
        Objects.requireNonNull(outputPath, "Output path must not be null");
        toFile(base64String, outputPath.toFile());
    }
    
    /**
     * 从 data-uri 字符串中提取纯 Base64 数据部分。
     *
     * @param base64String 带有前缀的 Base64 字符串
     * @return 纯 Base64 数据
     */
    private static String extractBase64Data(String base64String) {
        int commaIndex = base64String.indexOf(',');
        if (commaIndex == -1 || !base64String.contains(";base64")) {
            throw new IllegalArgumentException("Invalid Base64 string format. Expected 'data:[MIME-TYPE];base64,...'");
        }
        return base64String.substring(commaIndex + 1);
    }

    /**
     * 从 data-uri 字符串中提取 MIME 类型。
     *
     * @param base64String 带有前缀的 Base64 字符串
     * @return MIME 类型, 如果找不到则返回 null
     */
    public static String extractMimeType(String base64String) {
        Objects.requireNonNull(base64String, "Base64 string must not be null");
        Matcher matcher = BASE64_PREFIX_PATTERN.matcher(base64String);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }
}

