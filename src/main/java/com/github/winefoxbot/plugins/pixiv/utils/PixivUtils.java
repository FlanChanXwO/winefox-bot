package com.github.winefoxbot.plugins.pixiv.utils;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author FlanChan (badapple495@outlook.com)
 * @since 2026-01-14-15:11
 */
public final class PixivUtils {
    private PixivUtils() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }

    private static final String PIXIV_BASE = "https://www.pixiv.net";

    public static boolean isPixivArtworkUrl(String msg) {
        Pattern pattern = Pattern.compile(PIXIV_BASE + "/artworks/(\\d+)|illust_id=(\\d+)");
        return pattern.matcher(msg).find();
    }

    public static String extractPID(String msg) {
        if (msg == null) return null;
        if (msg.matches("\\d+")) return msg;

        Pattern pattern = Pattern.compile(PIXIV_BASE + "/artworks/(\\d+)|illust_id=(\\d+)");
        Matcher matcher = pattern.matcher(msg);
        if (matcher.find()) {
            for (int i = 1; i <= matcher.groupCount(); i++) {
                if (matcher.group(i) != null) return matcher.group(i);
            }
        }
        return null;
    }

    public static String extractUID(String msg) {
        if (msg == null) return null;
        if (msg.matches("\\d+")) return msg;

        Pattern pattern = Pattern.compile(PIXIV_BASE + "/users/(\\d+)");
        Matcher matcher = pattern.matcher(msg);
        if (matcher.find()) {
            for (int i = 1; i <= matcher.groupCount(); i++) {
                if (matcher.group(i) != null) return matcher.group(i);
            }
        }
        return null;
    }
}