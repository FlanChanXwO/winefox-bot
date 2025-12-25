package com.github.winefoxbot.service.core.impl;

import com.github.winefoxbot.config.HelpDocConfiguration;
import com.github.winefoxbot.init.HelpDocLoader;
import com.github.winefoxbot.model.dto.HelpDoc;
import com.github.winefoxbot.service.core.HelpImageService;
import com.mortennobel.imagescaling.ResampleFilters;
import com.mortennobel.imagescaling.ResampleOp;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.font.FontRenderContext;
import java.awt.font.LineBreakMeasurer;
import java.awt.font.TextAttribute;
import java.awt.image.BufferedImage;
import java.awt.image.ConvolveOp;
import java.awt.image.Kernel;
import java.io.IOException;
import java.io.InputStream;
import java.text.AttributedCharacterIterator;
import java.text.AttributedString;
import java.util.*;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

@Service
@RequiredArgsConstructor
@Slf4j
public class HelpImageServiceImpl implements HelpImageService {

    private final HelpDocLoader helpDocLoader;
    private final HelpDocConfiguration docConfiguration;

    // --- 绘图常量配置 ---
    private static final int WIDTH = 800;
    private static final int PADDING = 40;
    private static final int ROW_SPACING = 15;
    private static final int GROUP_TITLE_HEIGHT = 80;
    private static final int TABLE_HEADER_HEIGHT = 40;

    private static final Color TEXT_COLOR = Color.WHITE;
    private static final Color BORDER_COLOR = new Color(255, 255, 255, 100);
    private static final Color HEADER_BG_COLOR = new Color(0, 0, 0, 100);
    private static final Color GLASS_BG_COLOR = new Color(30, 30, 30, 150); // 默认玻璃背景色

    private static final Font GROUP_TITLE_FONT = new Font("思源黑体 CN Bold", Font.BOLD, 36);
    private static final Font TABLE_HEADER_FONT = new Font("思源黑体 CN Regular", Font.BOLD, 18);
    private static final Font CONTENT_FONT = new Font("思源黑体 CN Light", Font.PLAIN, 16);
    private static final Font CONTENT_BOLD_FONT = new Font("思源黑体 CN Regular", Font.BOLD, 16);

    private static final int[] COLUMN_WIDTHS = {100, 150, 290, 180}; // 名称, 权限/命令, 描述, 示例

    // 全局图片缓存，使用 ConcurrentHashMap 保证线程安全
    // 它只存在于内存中，应用重启后自动失效。
    private final Map<String, BufferedImage> imageCache = new ConcurrentHashMap<>();

    // 缓存键
    private static final String ALL_HELP_CACHE_KEY = "__CACHE_KEY_FOR_ALL_HELP__";


    /**
     * 生成包含所有帮助信息的图片。
     * 如果请求的是默认背景图片，将优先使用缓存。
     */
    @Override
    public BufferedImage generateAllHelpImage(Optional<InputStream> backgroundImageStream) throws IOException {
        // 如果提供了自定义背景，则不使用缓存，总是重新生成
        if (backgroundImageStream.isPresent()) {
            Map<String, List<HelpDoc>> allDocs = helpDocLoader.getGroupedDocs();
            return drawHelpImage(allDocs, backgroundImageStream);
        }

        // 使用 getOrGenerate 方法处理缓存逻辑
        return getOrGenerate(ALL_HELP_CACHE_KEY, () -> {
            try {
                Map<String, List<HelpDoc>> allDocs = helpDocLoader.getGroupedDocs();
                // 注意：这里传递的是 Optional.empty()，因为我们缓存的是默认背景的图片
                return drawHelpImage(allDocs, Optional.empty());
            } catch (IOException e) {
                // 在 Lambda 中，必须处理受检异常
                throw new RuntimeException(e); // 或者其他更优雅的异常包装方式
            }
        });
    }

    /**
     * 生成指定分组的帮助图片。
     * 如果请求的是默认背景图片，将优先使用缓存。
     */
    @Override
    public BufferedImage generateGroupHelpImage(String groupName, Optional<InputStream> backgroundImageStream) throws IOException {
        // 如果提供了自定义背景，则不使用缓存，总是重新生成
        if (backgroundImageStream.isPresent()) {
            Map<String, List<HelpDoc>> allDocs = helpDocLoader.getGroupedDocs();
            List<HelpDoc> groupDocs = allDocs.get(groupName);
            if (groupDocs == null || groupDocs.isEmpty()) {
                return null;
            }
            return drawHelpImage(Map.of(groupName, groupDocs), backgroundImageStream);
        }

        // 缓存的 Key 就是分组名 groupName
        return getOrGenerate(groupName, () -> {
            try {
                Map<String, List<HelpDoc>> allDocs = helpDocLoader.getGroupedDocs();
                List<HelpDoc> groupDocs = allDocs.get(groupName);
                if (groupDocs == null || groupDocs.isEmpty()) {
                    // 对于无法生成的内容（如空分组），返回 null，缓存逻辑会处理
                    return null;
                }
                // 注意：这里传递的是 Optional.empty()
                return drawHelpImage(Map.of(groupName, groupDocs), Optional.empty());
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }

    /**
     * 辅助方法：从缓存获取或生成并存入缓存。
     *
     * @param key 缓存的唯一键
     * @param generator 如果缓存未命中，用于生成新对象的 Supplier
     * @return 缓存中或新生成的 BufferedImage 对象
     */
    private BufferedImage getOrGenerate(String key, Supplier<BufferedImage> generator) {
        // computeIfAbsent 是 ConcurrentHashMap 的原子操作，非常适合此场景
        // 1. 它会检查 key 是否存在。
        // 2. 如果存在，直接返回对应的 value。
        // 3. 如果不存在，它会执行传入的 lambda (generator)，并将结果存入 map，然后返回结果。
        // 这一切都是线程安全的，可以防止多个线程同时为同一个 key 生成图片。
        return imageCache.computeIfAbsent(key, k -> generator.get());
    }

    @PreDestroy
    public void clearCache() {
        imageCache.clear();
        log.info("帮助图片缓存已清空。");
    }


    private BufferedImage drawHelpImage(Map<String, List<HelpDoc>> docsToDraw, Optional<InputStream> backgroundImageStream) throws IOException {
        // 1. 计算总高度
        int totalHeight = calculateTotalHeight(docsToDraw);

        // 2. 创建画布
        BufferedImage canvas = new BufferedImage(WIDTH, totalHeight, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = canvas.createGraphics();

        // 3. 设置渲染提示，抗锯齿
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        // 4. 绘制背景
        drawBackground(g2d, totalHeight, backgroundImageStream);

        // 5. 开始从上到下绘制内容
        int currentY = PADDING;

        List<Map.Entry<String, List<HelpDoc>>> sortedEntries = new ArrayList<>(docsToDraw.entrySet());

        // 定义一个优先级列表
        List<String> priorityOrder = docConfiguration.getGroupOrder();



        sortedEntries.sort(Comparator.comparingInt(entry -> {
            int index = priorityOrder.indexOf(entry.getKey());
            // 如果在优先级列表中找到了，就用它的索引排序；否则排在最后
            return index == -1 ? Integer.MAX_VALUE : index;
        }));

        for (Map.Entry<String, List<HelpDoc>> entry : sortedEntries) {
            String groupName = entry.getKey();
            List<HelpDoc> docs = entry.getValue();

            // 绘制分组标题
            drawGroupTitle(g2d, groupName, currentY);
            currentY += GROUP_TITLE_HEIGHT;

            // 绘制表格头部
            drawTableHeader(g2d, currentY);
            currentY += TABLE_HEADER_HEIGHT;

            // 绘制每一行功能
            for (HelpDoc doc : docs) {
                int rowHeight = calculateRowHeight(g2d, doc);
                drawRow(g2d, doc, currentY, rowHeight);
                currentY += rowHeight;
            }
            currentY += PADDING; // 分组间的额外间距
        }

        g2d.dispose();
        return canvas;
    }

    private void drawBackground(Graphics2D g2d, int height, Optional<InputStream> bgStream) throws IOException {
        if (bgStream.isPresent()) {
            try (InputStream is = bgStream.get()) {
                BufferedImage originalBg = ImageIO.read(is);
                if (originalBg == null) {
                    throw new IOException("Failed to read background image stream.");
                }
                // 缩放并裁剪背景图以适应画布宽度
                BufferedImage scaledBg = scaleAndCrop(originalBg, WIDTH, height);
                // 应用高斯模糊效果
                BufferedImage blurredBg = gaussianBlur(scaledBg);
                g2d.drawImage(blurredBg, 0, 0, null);
            } catch (Exception e) {
                log.error("Failed to process background image, falling back to default.", e);
                drawDefaultBackground(g2d, height);
            }
        } else {
            // 没有背景图，使用默认玻璃色
            drawDefaultBackground(g2d, height);
        }
    }

    private void drawDefaultBackground(Graphics2D g2d, int height) {
        g2d.setColor(GLASS_BG_COLOR);
        g2d.fillRect(0, 0, WIDTH, height);
    }

    // --- 绘图辅助方法 ---

    private void drawGroupTitle(Graphics2D g2d, String title, int y) {
        g2d.setFont(GROUP_TITLE_FONT);
        g2d.setColor(TEXT_COLOR);
        FontMetrics fm = g2d.getFontMetrics();
        int textWidth = fm.stringWidth(title);
        g2d.drawString(title, (WIDTH - textWidth) / 2, y + fm.getAscent());
    }

    private void drawTableHeader(Graphics2D g2d, int y) {
        g2d.setColor(HEADER_BG_COLOR);
        g2d.fillRect(PADDING, y, WIDTH - 2 * PADDING, TABLE_HEADER_HEIGHT);

        g2d.setColor(TEXT_COLOR);
        g2d.setFont(TABLE_HEADER_FONT);
        FontMetrics fm = g2d.getFontMetrics();

        String[] headers = {"功能名称", "权限", "功能描述", "可用命令"};
        int currentX = PADDING;
        for (int i = 0; i < headers.length; i++) {
            g2d.drawString(headers[i], currentX + 15, y + (TABLE_HEADER_HEIGHT - fm.getHeight()) / 2 + fm.getAscent());
            currentX += COLUMN_WIDTHS[i];
        }

        // 绘制表头竖线
        g2d.setColor(BORDER_COLOR);
        currentX = PADDING;
        for (int width : COLUMN_WIDTHS) {
            currentX += width;
            g2d.drawLine(currentX, y, currentX, y + TABLE_HEADER_HEIGHT);
        }
    }

    private void drawRow(Graphics2D g2d, HelpDoc doc, int y, int rowHeight) {
        int x = PADDING;

        // 绘制行背景和边框
        g2d.setColor(BORDER_COLOR);
        g2d.drawRect(x, y, WIDTH - 2 * PADDING, rowHeight);

        // 绘制内容并处理自动换行
        drawWrappedText(g2d, doc.getName(), x, y, COLUMN_WIDTHS[0], rowHeight, CONTENT_FONT);
        x += COLUMN_WIDTHS[0];
        g2d.drawLine(x, y, x, y + rowHeight);

        String perm =  doc.getPermission();
        drawWrappedText(g2d, perm, x, y, COLUMN_WIDTHS[1], rowHeight, CONTENT_FONT);
        x += COLUMN_WIDTHS[1];
        g2d.drawLine(x, y, x, y + rowHeight);

        drawWrappedText(g2d, doc.getDescription(), x, y, COLUMN_WIDTHS[2], rowHeight, CONTENT_FONT);
        x += COLUMN_WIDTHS[2];
        g2d.drawLine(x, y, x, y + rowHeight);

        drawWrappedText(g2d, doc.getFormattedCommands(), x, y, COLUMN_WIDTHS[3], rowHeight, CONTENT_FONT);
    }

    private void drawWrappedText(Graphics2D g2d, String text, int x, int y, int width, int height, Font font) {
        if (text == null || text.isEmpty()) {
            return; // 如果文本为空，直接返回
        }

        int textPadding = 15;
        int availableWidth = width - 2 * textPadding;

        FontRenderContext frc = g2d.getFontRenderContext();

        AttributedString attributedString = new AttributedString(text);
        attributedString.addAttribute(TextAttribute.FONT, font);
        attributedString.addAttribute(TextAttribute.FOREGROUND, TEXT_COLOR);

        AttributedCharacterIterator iterator = attributedString.getIterator();
        LineBreakMeasurer measurer = new LineBreakMeasurer(iterator, frc);

        // --- 核心修改：移除截断逻辑，信任行高计算结果 ---
        float currentY = y + textPadding; // 从顶部padding开始

        while (measurer.getPosition() < iterator.getEndIndex()) {
            // 限制布局宽度，防止无限循环或错误
            var layout = measurer.nextLayout(availableWidth);
            if (layout == null) {
                break;
            }

            // 向上移动光标到绘制基线的位置
            currentY += layout.getAscent();

            // 绘制当前行
            layout.draw(g2d, x + textPadding, currentY);

            // 向下移动光标，为下一行做准备
            currentY += layout.getDescent() + layout.getLeading();
        }
    }


    // --- 高度计算方法 ---

    private int calculateTotalHeight(Map<String, List<HelpDoc>> docsToDraw) {
        int totalHeight = PADDING; // 顶部 PADDING

        // 模拟一个Graphics2D对象以获取FontMetrics
        BufferedImage tempImg = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        Graphics2D tempG2d = tempImg.createGraphics();

        for (List<HelpDoc> docs : docsToDraw.values()) {
            totalHeight += GROUP_TITLE_HEIGHT;
            totalHeight += TABLE_HEADER_HEIGHT;
            for (HelpDoc doc : docs) {
                totalHeight += calculateRowHeight(tempG2d, doc);
            }
            totalHeight += PADDING; // 分组底部间距
        }
        tempG2d.dispose();
        return totalHeight;
    }

    private int calculateRowHeight(Graphics2D g2d, HelpDoc doc) {
        int minHeight = 60; // 最小行高
        int maxHeight = 0;

        maxHeight = Math.max(maxHeight, getTextHeight(g2d, doc.getName(), COLUMN_WIDTHS[0], CONTENT_FONT));
        maxHeight = Math.max(maxHeight, getTextHeight(g2d, doc.getPermission(), COLUMN_WIDTHS[1], CONTENT_FONT));
        maxHeight = Math.max(maxHeight, getTextHeight(g2d, doc.getDescription(), COLUMN_WIDTHS[2], CONTENT_FONT));
        maxHeight = Math.max(maxHeight, getTextHeight(g2d, doc.getFormattedCommands(), COLUMN_WIDTHS[3], CONTENT_FONT));

        // 使用 PADDING 作为垂直内边距，而不是 ROW_SPACING，保持统一
        // ROW_SPACING 更适合作为行与行之间的间距，而不是单元格内部的边距
        int cellPadding = 15; // 单元格垂直内边距
        return Math.max(minHeight, maxHeight + 2 * cellPadding);
    }


    private int getTextHeight(Graphics2D g2d, String text, int width, Font font) {
        int textPadding = 15;
        int availableWidth = width - 2 * textPadding;
        int height = 0;

        FontRenderContext frc = g2d.getFontRenderContext();

        AttributedString attributedString = new AttributedString(text);
        attributedString.addAttribute(TextAttribute.FONT, font);

        AttributedCharacterIterator iterator = attributedString.getIterator();
        LineBreakMeasurer measurer = new LineBreakMeasurer(iterator, frc);

        while (measurer.getPosition() < iterator.getEndIndex()) {
            var layout = measurer.nextLayout(availableWidth);
            if(layout == null) break;
            height += layout.getAscent() + layout.getDescent() + layout.getLeading();
        }
        return height;
    }

    // --- 图像处理方法 ---

    private BufferedImage scaleAndCrop(BufferedImage source, int targetWidth, int targetHeight) {
        double sourceAspect = (double) source.getWidth() / source.getHeight();
        double targetAspect = (double) targetWidth / targetHeight;

        int newWidth, newHeight;
        if (sourceAspect > targetAspect) { // 源图更宽，以高度为准进行缩放
            newHeight = targetHeight;
            newWidth = (int) (newHeight * sourceAspect);
        } else { // 源图更高或比例相同，以宽度为准进行缩放
            newWidth = targetWidth;
            newHeight = (int) (newWidth / sourceAspect);
        }

        ResampleOp resampleOp = new ResampleOp(newWidth, newHeight);
        resampleOp.setFilter(ResampleFilters.getLanczos3Filter());
        BufferedImage scaledImage = resampleOp.filter(source, null);

        // 裁剪
        int x = (newWidth - targetWidth) / 2;
        int y = (newHeight - targetHeight) / 2;
        return scaledImage.getSubimage(Math.max(0, x), Math.max(0, y), targetWidth, targetHeight);
    }

    private BufferedImage gaussianBlur(BufferedImage source) {
        // 一个简单的高斯模糊核
        float[] matrix = {
            1/16f, 2/16f, 1/16f,
            2/16f, 4/16f, 2/16f,
            1/16f, 2/16f, 1/16f,
        };
        Kernel kernel = new Kernel(3, 3, matrix);
        ConvolveOp op = new ConvolveOp(kernel, ConvolveOp.EDGE_NO_OP, null);
        // 多次应用以增强效果
        BufferedImage blurred = op.filter(source, null);
        blurred = op.filter(blurred, null);
        return op.filter(blurred, null);
    }
}