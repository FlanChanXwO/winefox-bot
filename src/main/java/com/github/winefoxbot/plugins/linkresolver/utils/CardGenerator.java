package com.github.winefoxbot.plugins.linkresolver.utils;

import com.github.winefoxbot.core.utils.DynamicResourceLoader;
import lombok.extern.slf4j.Slf4j;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.Ellipse2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.List;

@Slf4j
public class CardGenerator {

    private static final int WIDTH = 1080;
    private static final int PADDING = 60;
    private static final int AVATAR_SIZE = 120;
    private static final int FONT_SIZE_NAME = 40;
    private static final int FONT_SIZE_ID = 30;
    private static final int FONT_SIZE_TEXT = 36;
    private static final int FONT_SIZE_FOOTER = 28;

    private static final Color BG_COLOR = new Color(255, 255, 255);
    private static final Color TEXT_COLOR = new Color(30, 30, 30);
    private static final Color SUB_TEXT_COLOR = new Color(100, 100, 100);
    private static final Color LINK_COLOR = new Color(29, 161, 242);
    private static final Color BORDER_COLOR = new Color(230, 230, 230);

    public static File generateTwitterCard(String name, String screenName, String avatarUrl, String text,
                                           List<String> imageUrls, String dateStr,
                                           long likes, long retweets, String source, String tmpDir) {
        return generateCard(name, "@" + screenName, avatarUrl, text, imageUrls, dateStr,
                "❤ " + formatNum(likes) + "   \uD83D\uDD01 " + formatNum(retweets), source, tmpDir, "twitter");
    }

    public static File generateBilibiliCard(String title, String coverUrl, String upName, String upFaceUrl,
                                            String dateStr, String playCount, String danmakuCount,
                                            String likeCount, String coinCount, String favoriteCount, String shareCount,
                                            String summary, String tmpDir) {
        List<String> images = new ArrayList<>();
        if (coverUrl != null && !coverUrl.isEmpty()) {
            images.add(coverUrl);
        }
        // 使用图标代替文字
        // 播放量图标: assets/linkresolver/play.png
        // 弹幕量图标: assets/linkresolver/barrage.png (原总弹幕量.png)
        // 点赞图标: assets/linkresolver/like.png
        // 投币图标: assets/linkresolver/coin.png
        // 收藏图标: assets/linkresolver/favourite.png
        // 转发图标: assets/linkresolver/share.png
        
        // 顺序：播放量 -> 弹幕数 -> 点赞 -> 投币 -> 收藏 -> 转发
        String statsLine = "ICON:play.png " + playCount + 
                           "   ICON:barrage.png " + danmakuCount +
                           "   ICON:like.png " + likeCount +
                           "   ICON:coin.png " + coinCount +
                           "   ICON:favourite.png " + favoriteCount +
                           "   ICON:share.png " + shareCount;
        
        String content = title;
        if (summary != null && !summary.isEmpty()) {
            content += "\n\n" + summary;
        }
        return generateCard(upName, dateStr, upFaceUrl, content, images, null, statsLine, "Bilibili", tmpDir, "bilibili");
    }

    private static File generateCard(String name, String subName, String avatarUrl, String text,
                                     List<String> imageUrls, String dateStr,
                                     String statsLine, String sourceName, String tmpDir, String platform) {
        try {
            // 1. Calculate Height
            BufferedImage dummy = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
            Graphics2D gDummy = dummy.createGraphics();
            initGraphics(gDummy);
            
            int contentWidth = WIDTH - (PADDING * 2);
            int currentY = PADDING;

            // Header height
            currentY += AVATAR_SIZE + 20;

            // Text height
            if (text != null && !text.isEmpty()) {
                gDummy.setFont(new Font("Microsoft YaHei", Font.PLAIN, FONT_SIZE_TEXT));
                int textHeight = calculateTextHeight(gDummy, text, contentWidth, FONT_SIZE_TEXT);
                currentY += textHeight + 30;
            }

            // Images height
            int imagesHeight = 0;
            if (imageUrls != null && !imageUrls.isEmpty()) {
                if (imageUrls.size() == 1) imagesHeight = (int) (contentWidth * 9.0 / 16.0);
                else if (imageUrls.size() == 2) imagesHeight = (contentWidth - 20) / 2;
                else if (imageUrls.size() == 3) imagesHeight = (contentWidth - 20) / 2; 
                else if (imageUrls.size() == 4) imagesHeight = ((contentWidth - 20) / 2) * 2 + 20;
                else imagesHeight = ((contentWidth - 20) / 3) * 2 + 20; // 5+ images
                
                currentY += imagesHeight + 30;
            }

            // Footer height
            currentY += 60; 

            int totalHeight = currentY + PADDING;
            
            // 2. Draw
            BufferedImage image = new BufferedImage(WIDTH, totalHeight, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = image.createGraphics();
            initGraphics(g);

            // Background
            g.setColor(BG_COLOR);
            g.fillRect(0, 0, WIDTH, totalHeight);

            int drawY = PADDING;

            // Header
            BufferedImage avatar = fetchImage(avatarUrl);
            if (avatar != null) {
                Shape circle = new Ellipse2D.Double(PADDING, drawY, AVATAR_SIZE, AVATAR_SIZE);
                g.setClip(circle);
                g.drawImage(avatar, PADDING, drawY, AVATAR_SIZE, AVATAR_SIZE, null);
                g.setClip(null);
                
                // Avatar border
                g.setColor(new Color(0,0,0,20));
                g.setStroke(new BasicStroke(1f));
                g.draw(circle);
            }

            g.setColor(TEXT_COLOR);
            g.setFont(new Font("Microsoft YaHei", Font.BOLD, FONT_SIZE_NAME));
            g.drawString(name, PADDING + AVATAR_SIZE + 30, drawY + 50);

            g.setColor(SUB_TEXT_COLOR);
            g.setFont(new Font("Microsoft YaHei", Font.PLAIN, FONT_SIZE_ID));
            String subInfo = subName;
            if (dateStr != null) subInfo += " · " + dateStr;
            g.drawString(subInfo, PADDING + AVATAR_SIZE + 30, drawY + 100);

            // Platform Icon (Top Right)
            try {
                String iconPath = "assets/linkresolver/" + platform + ".png";
                try (InputStream is = DynamicResourceLoader.getInputStream(iconPath)) {
                    if (is != null) {
                        BufferedImage icon = ImageIO.read(is);
                        
                        // 保持长宽比
                        int targetHeight = 60;
                        int originalWidth = icon.getWidth();
                        int originalHeight = icon.getHeight();
                        
                        double ratio = (double) targetHeight / originalHeight;
                        int targetWidth = (int) (originalWidth * ratio);
                        
                        g.drawImage(icon, WIDTH - PADDING - targetWidth, drawY, targetWidth, targetHeight, null);
                    }
                }
            } catch (Exception e) {
                // ignore
            }

            drawY += AVATAR_SIZE + 30;

            // Text
            if (text != null && !text.isEmpty()) {
                g.setColor(TEXT_COLOR);
                g.setFont(new Font("Microsoft YaHei", Font.PLAIN, FONT_SIZE_TEXT));
                drawY = drawWrappedText(g, text, PADDING, drawY, contentWidth, FONT_SIZE_TEXT);
                drawY += 30;
            }

            // Images
            if (imageUrls != null && !imageUrls.isEmpty()) {
                drawImages(g, imageUrls, PADDING, drawY, contentWidth);
                int h;
                if (imageUrls.size() == 1) h = (int) (contentWidth * 9.0 / 16.0);
                else if (imageUrls.size() == 2) h = (contentWidth - 20) / 2;
                else if (imageUrls.size() == 3) h = (contentWidth - 20) / 2; 
                else if (imageUrls.size() == 4) h = ((contentWidth - 20) / 2) * 2 + 20;
                else h = ((contentWidth - 20) / 3) * 2 + 20;
                drawY += h + 30;
            }

            // Footer (Stats + Source)
            g.setColor(BORDER_COLOR);
            g.drawLine(PADDING, drawY, WIDTH - PADDING, drawY);
            drawY += 40;

            g.setColor(SUB_TEXT_COLOR);
            g.setFont(new Font("Microsoft YaHei", Font.PLAIN, FONT_SIZE_FOOTER));
            
            // Draw Stats with Icons if needed
            if (statsLine.contains("ICON:")) {
                drawStatsWithIcons(g, statsLine, PADDING, drawY + 10);
            } else {
                g.drawString(statsLine, PADDING, drawY + 10);
            }
            
            // 移除右下角的 logo 文字
            // String footerSource = sourceName;
            // int sourceWidth = g.getFontMetrics().stringWidth(footerSource);
            // g.drawString(footerSource, WIDTH - PADDING - sourceWidth, drawY + 10);

            g.dispose();

            File outDir = new File(tmpDir);
            if (!outDir.exists()) outDir.mkdirs();
            File outFile = new File(outDir, platform + "_" + System.currentTimeMillis() + ".png");
            ImageIO.write(image, "png", outFile);
            return outFile;

        } catch (Exception e) {
            log.error("Failed to generate card", e);
            return null;
        }
    }

    private static void drawStatsWithIcons(Graphics2D g, String statsLine, int x, int y) {
        String[] parts = statsLine.split(" ");
        int currentX = x;
        int iconSize = 32; // Increased icon size from 24 to 32
        
        for (String part : parts) {
            if (part.startsWith("ICON:")) {
                String iconName = part.substring(5);
                try {
                    String iconPath = "assets/linkresolver/" + iconName;
                    try (InputStream is = DynamicResourceLoader.getInputStream(iconPath)) {
                        if (is != null) {
                            BufferedImage icon = ImageIO.read(is);
                            // Draw icon vertically centered relative to text baseline
                            // Text baseline is y. Icon top should be y - ascent + (height - iconSize)/2 ?
                            // Simple approximation: y - iconSize + 2 (adjust manually)
                            g.drawImage(icon, currentX, y - iconSize + 4, iconSize, iconSize, null);
                            currentX += iconSize + 5;
                        }
                    }
                } catch (Exception e) {
                    // ignore
                }
            } else if (!part.isEmpty()) {
                g.drawString(part, currentX, y);
                currentX += g.getFontMetrics().stringWidth(part) + 15; // Add spacing after text
            }
        }
    }

    private static void drawImages(Graphics2D g, List<String> urls, int x, int y, int w) {
        int gap = 20;
        int count = urls.size();
        
        if (count == 1) {
            BufferedImage img = fetchImage(urls.get(0));
            if (img != null) {
                int h = (int) (w * 9.0 / 16.0);
                drawImageRounded(g, img, x, y, w, h);
            }
        } else if (count == 2) {
            int itemW = (w - gap) / 2;
            int itemH = itemW;
            for (int i = 0; i < 2; i++) {
                BufferedImage img = fetchImage(urls.get(i));
                if (img != null) drawImageRounded(g, img, x + (itemW + gap) * i, y, itemW, itemH);
            }
        } else if (count == 3) {
            // 1 big left, 2 small right? Or 3 in row? Let's do 3 in row for simplicity or 1 big 2 small.
            // Let's do 3 in a row.
            int itemW = (w - gap * 2) / 3;
            int itemH = itemW;
            for (int i = 0; i < 3; i++) {
                BufferedImage img = fetchImage(urls.get(i));
                if (img != null) drawImageRounded(g, img, x + (itemW + gap) * i, y, itemW, itemH);
            }
        } else if (count == 4) {
            int itemW = (w - gap) / 2;
            int itemH = itemW;
            for (int i = 0; i < 4; i++) {
                BufferedImage img = fetchImage(urls.get(i));
                if (img != null) {
                    int r = i / 2;
                    int c = i % 2;
                    drawImageRounded(g, img, x + (itemW + gap) * c, y + (itemH + gap) * r, itemW, itemH);
                }
            }
        } else {
            // 9 grid style for 5+
            int itemW = (w - gap * 2) / 3;
            int itemH = itemW;
            int limit = Math.min(count, 9);
            for (int i = 0; i < limit; i++) {
                BufferedImage img = fetchImage(urls.get(i));
                if (img != null) {
                    int r = i / 3;
                    int c = i % 3;
                    drawImageRounded(g, img, x + (itemW + gap) * c, y + (itemH + gap) * r, itemW, itemH);
                }
            }
        }
    }

    private static void drawImageRounded(Graphics2D g, BufferedImage img, int x, int y, int w, int h) {
        Shape oldClip = g.getClip();
        RoundRectangle2D rect = new RoundRectangle2D.Float(x, y, w, h, 20, 20);
        g.setClip(rect);
        
        // Scale image to fill (center crop)
        double sx = (double) w / img.getWidth();
        double sy = (double) h / img.getHeight();
        double scale = Math.max(sx, sy);
        int sw = (int) (img.getWidth() * scale);
        int sh = (int) (img.getHeight() * scale);
        int dx = x + (w - sw) / 2;
        int dy = y + (h - sh) / 2;
        
        g.drawImage(img, dx, dy, sw, sh, null);
        g.setClip(oldClip);
        
        g.setColor(new Color(0,0,0,20));
        g.setStroke(new BasicStroke(1f));
        g.draw(rect);
    }

    private static void initGraphics(Graphics2D g) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
    }

    private static int calculateTextHeight(Graphics2D g, String text, int maxWidth, int fontSize) {
        FontMetrics fm = g.getFontMetrics();
        int lineHeight = fm.getHeight() + 10;
        int lines = 0;
        for (String paragraph : text.split("\n")) {
            StringBuilder line = new StringBuilder();
            for (char c : paragraph.toCharArray()) {
                if (fm.stringWidth(line.toString() + c) > maxWidth) {
                    lines++;
                    line = new StringBuilder();
                }
                line.append(c);
            }
            lines++;
        }
        return lines * lineHeight;
    }

    private static int drawWrappedText(Graphics2D g, String text, int x, int y, int maxWidth, int fontSize) {
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
        return curY - fm.getAscent(); // Return bottom Y
    }

    private static BufferedImage fetchImage(String urlString) {
        if (urlString == null || urlString.isEmpty()) return null;
        try {
            URLConnection conn = new URL(urlString).openConnection();
            conn.setRequestProperty("User-Agent", "Mozilla/5.0");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            return ImageIO.read(conn.getInputStream());
        } catch (Exception e) {
            return null;
        }
    }

    private static String formatNum(long num) {
        if (num >= 10000) {
            return String.format("%.1f万", num / 10000.0);
        }
        return String.valueOf(num);
    }
}
