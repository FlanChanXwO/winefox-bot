package com.github.winefoxbot.plugins.linkresolver.util;

import com.github.winefoxbot.plugins.linkresolver.config.LinkResolverConfig;
import com.github.winefoxbot.core.service.file.FileStorageService;
import com.github.winefoxbot.core.util.DynamicResourceLoader;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.Ellipse2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.awt.image.ConvolveOp;
import java.awt.image.Kernel;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Objects;

@Slf4j
@Component
@RequiredArgsConstructor
public class CardGenerator {

    private static final int WIDTH = 1080;
    private static final int PADDING = 60;
    private static final int AVATAR_SIZE = 120;
    private static final int FONT_SIZE_NAME = 40;
    private static final int FONT_SIZE_ID = 30;
    private static final int FONT_SIZE_TITLE = 40; // 新增标题字体大小
    private static final int FONT_SIZE_TEXT = 36;
    private static final int FONT_SIZE_FOOTER = 28;

    private static final Color BG_COLOR = new Color(255, 255, 255);
    private static final Color NAME_COLOR = new Color(0, 0, 0);
    private static final Color TITLE_COLOR = new Color(0, 0, 0); // 新增标题颜色
    private static final Color TEXT_COLOR = new Color(51, 51, 51);
    private static final Color SUB_TEXT_COLOR = new Color(153, 153, 153);
    private static final Color BORDER_COLOR = new Color(230, 230, 230);
    private static final int BLUR_RADIUS = 60;

    private static final String ICON_PATH = "assets/linkresolver/icon/";
    private static final String PLATFORM_ICON_PATH = "assets/linkresolver/platform/";
    private static final String FONT_NAME = "Noto Sans SC Regular";

    private final OkHttpClient httpClient;
    private final FileStorageService fileStorageService;
    private final LinkResolverConfig linkResolverConfig;

    @Data
    @AllArgsConstructor
    public static class CardStatistic {
        private String iconName;
        private String text;
    }

    // 修改 generateCard 方法签名，增加 title 参数
    public Path generateCard(String name, String subName, String avatarUrl, String title, String text,
                             List<String> imageUrls, String dateStr,
                             List<CardStatistic> statistics,
                             String platform, boolean isSensitive, double singleImageAspectRatio, boolean hasVideo,
                             String cacheKey) {
        try {
            BufferedImage dummy = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
            Graphics2D gDummy = dummy.createGraphics();
            initGraphics(gDummy);

            int contentWidth = WIDTH - (PADDING * 2);
            int currentY = PADDING + AVATAR_SIZE + 20;

            // 1. 计算标题高度
            if (title != null && !title.isEmpty()) {
                gDummy.setFont(new Font(FONT_NAME, Font.BOLD, FONT_SIZE_TITLE));
                int titleHeight = calculateTextHeight(gDummy, title, contentWidth);
                currentY += titleHeight + 20; // 标题和正文之间的间距
            }

            // 2. 计算正文高度
            if (text != null && !text.isEmpty()) {
                gDummy.setFont(new Font(FONT_NAME, Font.PLAIN, FONT_SIZE_TEXT));
                int textHeight = calculateTextHeight(gDummy, text, contentWidth);
                currentY += textHeight + 30;
            }

            // 3. 计算图片区域高度
            int imagesHeight = 0;
            if (imageUrls != null && !imageUrls.isEmpty()) {
                if (imageUrls.size() == 1) {
                    double ratio = singleImageAspectRatio > 0 ? singleImageAspectRatio : (16.0 / 9.0);
                    ratio = Math.max(ratio, 0.3);
                    imagesHeight = (int) (contentWidth / ratio);
                } else if (imageUrls.size() <= 4) {
                    imagesHeight = (contentWidth - 20) / 2;
                    if (imageUrls.size() > 2) imagesHeight = ((contentWidth - 20) / 2) * 2 + 20;
                } else {
                    imagesHeight = ((contentWidth - 20) / 3) * 2 + 20;
                }
                currentY += imagesHeight + 30;
            }

            // 4. 动态计算统计数据区域高度
            int statsHeight = 0;
            if (statistics != null && !statistics.isEmpty()) {
                gDummy.setFont(new Font(FONT_NAME, Font.PLAIN, FONT_SIZE_FOOTER));
                // 预留分割线高度 + 统计区域高度
                statsHeight = 40 + calculateStatisticsHeight(gDummy, statistics, contentWidth);
                currentY += statsHeight;
            } else {
                currentY += 20;
            }

            int totalHeight = currentY + PADDING;

            // --- 开始绘制 ---
            BufferedImage image = new BufferedImage(WIDTH, totalHeight, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = image.createGraphics();
            initGraphics(g);

            g.setColor(BG_COLOR);
            g.fillRect(0, 0, WIDTH, totalHeight);

            int drawY = PADDING;

            // 绘制头像
            BufferedImage avatar = fetchImage(avatarUrl);
            if (avatar == null) {
                g.setColor(Color.LIGHT_GRAY);
                g.fillOval(PADDING, drawY, AVATAR_SIZE, AVATAR_SIZE);
            } else {
                Shape circle = new Ellipse2D.Double(PADDING, drawY, AVATAR_SIZE, AVATAR_SIZE);
                g.setClip(circle);
                g.drawImage(avatar, PADDING, drawY, AVATAR_SIZE, AVATAR_SIZE, null);
                g.setClip(null);
                g.setColor(new Color(0, 0, 0, 20));
                g.setStroke(new BasicStroke(1f));
                g.draw(circle);
            }

            // 绘制名字
            g.setColor(NAME_COLOR);
            g.setFont(new Font(FONT_NAME, Font.BOLD, FONT_SIZE_NAME));
            g.drawString(name, PADDING + AVATAR_SIZE + 30, drawY + 50);

            // 绘制副标题（ID/时间）
            g.setColor(SUB_TEXT_COLOR);
            g.setFont(new Font(FONT_NAME, Font.PLAIN, FONT_SIZE_ID));
            String subInfo = subName;
            if (dateStr != null) subInfo += " · " + dateStr;
            g.drawString(subInfo, PADDING + AVATAR_SIZE + 30, drawY + 100);

            // 绘制平台图标
            drawPlatformIcon(g, platform, drawY);

            drawY += AVATAR_SIZE + 30;

            // 绘制标题
            if (title != null && !title.isEmpty()) {
                g.setColor(TITLE_COLOR);
                g.setFont(new Font(FONT_NAME, Font.BOLD, FONT_SIZE_TITLE));
                drawY = drawWrappedText(g, title, PADDING, drawY, contentWidth);
                drawY += 20;
            }

            // 绘制正文
            if (text != null && !text.isEmpty()) {
                g.setColor(TEXT_COLOR);
                g.setFont(new Font(FONT_NAME, Font.PLAIN, FONT_SIZE_TEXT));
                drawY = drawWrappedText(g, text, PADDING, drawY, contentWidth);
                drawY += 30;
            }

            // 绘制图片网格
            if (imageUrls != null && !imageUrls.isEmpty()) {
                drawImages(g, imageUrls, PADDING, drawY, contentWidth, imagesHeight, isSensitive, hasVideo);
                drawY += imagesHeight + 30;
            }

            // 绘制统计数据
            if (statistics != null && !statistics.isEmpty()) {
                g.setColor(BORDER_COLOR);
                g.drawLine(PADDING, drawY, WIDTH - PADDING, drawY);
                drawY += 40; // 分割线后的间距

                g.setColor(SUB_TEXT_COLOR);
                g.setFont(new Font(FONT_NAME, Font.PLAIN, FONT_SIZE_FOOTER));
                drawStatistics(g, statistics, PADDING, drawY + 10, contentWidth);
            }

            g.dispose();

            // 保存文件逻辑
            File tempDir = new File(linkResolverConfig.getTmpPath());
            if (!tempDir.exists()) {
                tempDir.mkdirs();
            }
            File tempFile = File.createTempFile("card-", ".png", tempDir);
            try {
                ImageIO.write(image, "png", tempFile);
                try (InputStream is = new FileInputStream(tempFile)) {
                    return fileStorageService.saveFileByCacheKey(cacheKey, is, Duration.ofMinutes(10));
                }
            } finally {
                if (tempFile.exists()) {
                    tempFile.delete();
                }
            }
        } catch (Exception e) {
            log.error("Failed to generate card", e);
            return null;
        }
    }

    // --- 新增：计算统计数据区域的高度 ---
    private int calculateStatisticsHeight(Graphics2D g, List<CardStatistic> stats, int maxWidth) {
        if (stats == null || stats.isEmpty()) return 0;

        FontMetrics fm = g.getFontMetrics();
        int iconSize = 32;
        int gap = 15; // 图标和文字的间距
        int itemGap = 40; // 项目之间的间距
        int lineHeight = Math.max(iconSize, fm.getHeight()) + 20; // 行高（增加纵向间距）

        int currentX = 0;
        int rows = 1;

        for (CardStatistic stat : stats) {
            int textWidth = (stat.getText() != null) ? fm.stringWidth(stat.getText()) : 0;
            // 计算当前这个统计项所需的总宽度 (图标 + 间距 + 文字)
            int itemWidth = iconSize + gap + textWidth;

            // 如果当前行放不下这个项目，换行
            if (currentX + itemWidth > maxWidth) {
                rows++;
                currentX = 0;
            }

            currentX += itemWidth + itemGap;
        }

        return rows * lineHeight;
    }

    // --- 修改：绘制统计数据（支持换行） ---
    private void drawStatistics(Graphics2D g, List<CardStatistic> stats, int startX, int startY, int maxWidth) {
        int currentX = startX;
        int currentY = startY;

        int iconSize = 32;
        int gap = 15;
        int itemGap = 40;
        int lineHeight = Math.max(iconSize, g.getFontMetrics().getHeight()) + 20;

        for (CardStatistic stat : stats) {
            int textWidth = (stat.getText() != null) ? g.getFontMetrics().stringWidth(stat.getText()) : 0;
            int itemWidth = iconSize + gap + textWidth;

            // 检查是否需要换行 (currentX 加上当前项宽度 是否超过 边界)
            // 注意：startX 是左边距，maxWidth 是内容区域宽度
            if (currentX - startX + itemWidth > maxWidth) {
                currentX = startX;
                currentY += lineHeight;
            }

            // 绘制图标
            if (stat.getIconName() != null) {
                try (InputStream is = DynamicResourceLoader.getInputStream(ICON_PATH + stat.getIconName())) {
                    if (is != null) {
                        BufferedImage icon = ImageIO.read(is);
                        // icon 稍微垂直居中调整
                        g.drawImage(icon, currentX, currentY - iconSize + 4, iconSize, iconSize, null);
                    }
                } catch (Exception ignored) {}
            }

            // 绘制文字
            if (stat.getText() != null) {
                // 文字的位置 = 当前X + 图标 + 间距
                g.drawString(stat.getText(), currentX + iconSize + gap, currentY);
            }

            // 移动X到下一个项目位置
            currentX += itemWidth + itemGap;
        }
    }

    private void drawPlatformIcon(Graphics2D g, String platform, int y) {
        try {
            String iconPath = PLATFORM_ICON_PATH + platform + ".png";
            try (InputStream is = DynamicResourceLoader.getInputStream(iconPath)) {
                if (is != null) {
                    BufferedImage icon = ImageIO.read(is);
                    int targetHeight = 60;
                    double ratio = (double) targetHeight / icon.getHeight();
                    int targetWidth = (int) (icon.getWidth() * ratio);
                    g.drawImage(icon, WIDTH - PADDING - targetWidth, y, targetWidth, targetHeight, null);
                }
            }
        } catch (Exception ignored) {}
    }

    private void drawImages(Graphics2D g, List<String> urls, int x, int y, int w, int totalH, boolean isSensitive, boolean hasVideo) {
        int gap = 20;
        int count = urls.size();
        if (count == 1) {
            BufferedImage img = fetchAndProcessImage(urls.get(0), isSensitive);
            if (img != null) {
                drawImageRounded(g, img, x, y, w, totalH, true);
                if (hasVideo) {
                    drawPlayIcon(g, x, y, w, totalH);
                }
            }
        } else {
            int cols = (count <= 4) ? 2 : 3;
            int itemW = (w - (gap * (cols - 1))) / cols;
            int itemH = itemW;
            for (int i = 0; i < Math.min(count, 9); i++) {
                BufferedImage img = fetchAndProcessImage(urls.get(i), isSensitive);
                if (img != null) {
                    int r = i / cols;
                    int c = i % cols;
                    int imgX = x + (itemW + gap) * c;
                    int imgY = y + (itemH + gap) * r;
                    drawImageRounded(g, img, imgX, imgY, itemW, itemH, false);
                    if (hasVideo && i == 0) {
                        drawPlayIcon(g, imgX, imgY, itemW, itemH);
                    }
                }
            }
        }
    }

    private void drawPlayIcon(Graphics2D g, int x, int y, int w, int h) {
        try (InputStream is = DynamicResourceLoader.getInputStream(ICON_PATH + "play.png")) {
            if (is != null) {
                BufferedImage icon = ImageIO.read(is);
                int iconSize = Math.min(w, h) / 4;
                iconSize = Math.min(iconSize, 128);
                int iconX = x + (w - iconSize) / 2;
                int iconY = y + (h - iconSize) / 2;
                g.drawImage(icon, iconX, iconY, iconSize, iconSize, null);
            }
        } catch (Exception e) {
            log.warn("Failed to load or draw play icon", e);
        }
    }

    private BufferedImage fetchAndProcessImage(String url, boolean blur) {
        BufferedImage img = fetchImage(url);
        if (img != null && blur) return blurImage(img);
        return img;
    }

    private BufferedImage blurImage(BufferedImage source) {
        if (BLUR_RADIUS <= 0) return source;
        int radius = Math.max(1, BLUR_RADIUS);

        int padding = radius;
        int newWidth = source.getWidth() + 2 * padding;
        int newHeight = source.getHeight() + 2 * padding;
        BufferedImage paddedImage = new BufferedImage(newWidth, newHeight, source.getType());
        Graphics2D g = paddedImage.createGraphics();

        g.drawImage(source, padding, padding, null);

        g.drawImage(source.getSubimage(0, 0, source.getWidth(), 1), padding, 0, source.getWidth(), padding, null);
        g.drawImage(source.getSubimage(0, source.getHeight() - 1, source.getWidth(), 1), padding, newHeight - padding, source.getWidth(), padding, null);
        g.drawImage(source.getSubimage(0, 0, 1, source.getHeight()), 0, padding, padding, source.getHeight(), null);
        g.drawImage(source.getSubimage(source.getWidth() - 1, 0, 1, source.getHeight()), newWidth - padding, padding, padding, source.getHeight(), null);
        g.drawImage(source.getSubimage(0, 0, 1, 1), 0, 0, padding, padding, null);
        g.drawImage(source.getSubimage(source.getWidth() - 1, 0, 1, 1), newWidth - padding, 0, padding, padding, null);
        g.drawImage(source.getSubimage(0, source.getHeight() - 1, 1, 1), 0, newHeight - padding, padding, padding, null);
        g.drawImage(source.getSubimage(source.getWidth() - 1, source.getHeight() - 1, 1, 1), newWidth - padding, newHeight - padding, padding, padding, null);
        g.dispose();

        int size = radius * radius;
        float[] matrix = new float[size];
        for (int i = 0; i < size; i++) matrix[i] = 1.0f / size;
        ConvolveOp op = new ConvolveOp(new Kernel(radius, radius, matrix), ConvolveOp.EDGE_NO_OP, null);
        BufferedImage blurredPadded = op.filter(paddedImage, null);

        return blurredPadded.getSubimage(padding, padding, source.getWidth(), source.getHeight());
    }

    private void drawImageRounded(Graphics2D g, BufferedImage img, int x, int y, int w, int h, boolean fitContains) {
        Shape oldClip = g.getClip();
        RoundRectangle2D rect = new RoundRectangle2D.Float(x, y, w, h, 20, 20);
        g.setClip(rect);
        if (fitContains) {
            g.drawImage(img, x, y, w, h, null);
        } else {
            double sx = (double) w / img.getWidth();
            double sy = (double) h / img.getHeight();
            double scale = Math.max(sx, sy);
            int sw = (int) (img.getWidth() * scale);
            int sh = (int) (img.getHeight() * scale);
            int dx = x + (w - sw) / 2;
            int dy = y + (h - sh) / 2;
            g.drawImage(img, dx, dy, sw, sh, null);
        }
        g.setClip(oldClip);
        g.setColor(new Color(0,0,0,20));
        g.setStroke(new BasicStroke(1f));
        g.draw(rect);
    }

    private void initGraphics(Graphics2D g) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
    }

    private int calculateTextHeight(Graphics2D g, String text, int maxWidth) {
        FontMetrics fm = g.getFontMetrics();
        int lineHeight = fm.getHeight() + 10;
        int lines = 0;
        for (String paragraph : text.split("\n")) {
            StringBuilder line = new StringBuilder();
            for (char c : paragraph.toCharArray()) {
                if (fm.stringWidth(line.toString() + c) > maxWidth) { lines++; line = new StringBuilder(); }
                line.append(c);
            }
            lines++;
        }
        return Math.max(lines, 1) * lineHeight;
    }

    private int drawWrappedText(Graphics2D g, String text, int x, int y, int maxWidth) {
        FontMetrics fm = g.getFontMetrics();
        int lineHeight = fm.getHeight() + 10;
        int curY = y + fm.getAscent();
        for (String paragraph : text.split("\n")) {
            StringBuilder line = new StringBuilder();
            for (char c : paragraph.toCharArray()) {
                if (fm.stringWidth(line.toString() + c) > maxWidth) {
                    g.drawString(line.toString(), x, curY);
                    curY += lineHeight;
                    line = new StringBuilder();
                }
                line.append(c);
            }
            if (line.length() > 0) {
                g.drawString(line.toString(), x, curY);
                curY += lineHeight;
            }
        }
        return curY - fm.getAscent();
    }

    private BufferedImage fetchImage(String urlString) {
        if (urlString == null || urlString.isEmpty()) return null;
        Request request = new Request.Builder().url(urlString).build();
        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) return null;
            return ImageIO.read(Objects.requireNonNull(response.body()).byteStream());
        } catch (Exception e) {
            log.warn("Image fetch failed: {}", urlString, e);
            return null;
        }
    }
}