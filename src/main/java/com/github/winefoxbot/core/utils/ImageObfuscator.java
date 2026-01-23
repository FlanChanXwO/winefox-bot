package com.github.winefoxbot.core.utils;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.RandomUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 图片混淆包装工具类
 * 用于处理无法通过审核的图片，通过添加边框、噪点、微旋转等方式绕过检测
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ImageObfuscator {

    private final OkHttpClient okHttpClient;
    private static final String TEMP_DIR = "setu_obfuscated_tmp";

    /**
     * 包装单个对象（自动识别类型）
     */
    public Path wrap(Object input) {
        return switch (input) {
            case Path p -> wrapLocalImage(p);
            case URI u -> wrapNetworkImage(u);
            case String s -> wrapNetworkImage(URI.create(s));
            case null -> null;
            default -> throw new IllegalArgumentException("不支持的图片输入类型: " + input.getClass());
        };
    }

    /**
     * 批量包装列表（支持混合类型）
     * 使用虚拟线程并发处理
     */
    public List<Path> wrap(List<?> inputs) {
        if (inputs == null || inputs.isEmpty()) return List.of();

        // JDK 21 虚拟线程
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var futures = inputs.stream()
                    .map(input -> CompletableFuture.supplyAsync(() -> {
                        try {
                            return wrap(input);
                        } catch (Exception e) {
                            log.error("图片包装失败: {}", input, e);
                            return null;
                        }
                    }, executor))
                    .toList();

            return futures.stream()
                    .map(CompletableFuture::join)
                    .filter(Objects::nonNull)
                    .toList();
        }
    }

    // ================= 内部实现方法 =================

    private Path wrapLocalImage(Path path) {
        try (InputStream is = Files.newInputStream(path)) {
            return processImageStream(is);
        } catch (IOException e) {
            log.error("读取本地图片失败: {}", path, e);
            throw new RuntimeException("本地图片读取失败", e);
        }
    }

    private Path wrapNetworkImage(URI uri) {
        Request request = new Request.Builder().url(uri.toString()).build();
        try (Response response = okHttpClient.newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) {
                log.warn("网络图片下载失败: {}", uri);
                return null;
            }
            try (InputStream is = response.body().byteStream()) {
                return processImageStream(is);
            }
        } catch (IOException e) {
            log.error("下载图片流失败: {}", uri, e);
            return null;
        }
    }

    /**
     * 核心混淆逻辑
     * 策略：
     * 1. 扩充画布（增加边框）
     * 2. 随机背景色
     * 3. 极微小的随机旋转 (-2 ~ 2度)
     * 4. 重新编码去除元数据
     */
    private Path processImageStream(InputStream inputStream) throws IOException {
        BufferedImage original = ImageIO.read(inputStream);
        if (original == null) throw new IOException("无法解码图片流");

        int width = original.getWidth();
        int height = original.getHeight();

        // 1. 计算新尺寸（增加 2% - 5% 的边框）
        int padding = Math.max(20, Math.min(width, height) / 20);
        int newWidth = width + padding * 2;
        int newHeight = height + padding * 2;

        BufferedImage output = new BufferedImage(newWidth, newHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = output.createGraphics();

        // 开启抗锯齿
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);

        // 2. 填充随机浅色背景（破坏纯色背景特征）
        Color randomBg = new Color(
                240 + RandomUtil.randomInt(15),
                240 + RandomUtil.randomInt(15),
                240 + RandomUtil.randomInt(15)
        );
        g2d.setColor(randomBg);
        g2d.fillRect(0, 0, newWidth, newHeight);

        // 3. 微量旋转（核心：破坏感知哈希）
        // 角度范围：-2.0 到 2.0 度
        double angle = (RandomUtil.randomDouble() * 4) - 2;
        g2d.rotate(Math.toRadians(angle), newWidth / 2.0, newHeight / 2.0);

        // 4. 绘制原图（居中）
        g2d.drawImage(original, padding, padding, null);

        // 5. 添加极细微的干扰线（可选，破坏OCR）
        g2d.setColor(new Color(255, 255, 255, 30)); // 极淡的白色
        for (int i = 0; i < 5; i++) {
            int x1 = RandomUtil.randomInt(newWidth);
            int y1 = RandomUtil.randomInt(newHeight);
            int x2 = RandomUtil.randomInt(newWidth);
            int y2 = RandomUtil.randomInt(newHeight);
            g2d.setStroke(new BasicStroke(RandomUtil.randomInt(1, 3)));
            g2d.drawLine(x1, y1, x2, y2);
        }

        g2d.dispose();

        // 6. 保存到临时文件
        Path tempDir = Path.of(System.getProperty("java.io.tmpdir"), TEMP_DIR);
        if (!Files.exists(tempDir)) {
            Files.createDirectories(tempDir);
        }

        String filename = "obfuscated_" + IdUtil.fastSimpleUUID() + ".jpg";
        Path targetPath = tempDir.resolve(filename);

        // 使用 JPG 保存通常体积更小且自带压缩噪点
        ImageIO.write(output, "jpg", targetPath.toFile());

        return targetPath;
    }
    
    /**
     * 清理临时目录（建议配合 @Scheduled 使用）
     */
    public void cleanTempFiles() {
        Path tempDir = Path.of(System.getProperty("java.io.tmpdir"), TEMP_DIR);
        FileUtil.del(tempDir);
    }
}
