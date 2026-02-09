package com.github.winefoxbot.plugins.linkresolver.utils;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 提供：
 *  - extract 类似函数：从消息文本中推断出对应的 API URL 或视频/房间 id
 *  - httpGetJson(url)：同步请求并返回 JsonNode
 *  - expandShortLink：处理 b23.tv 短链（跟随重定向）
 */
public class BiliUtils {

    // 正则表达式区域 (修正和优化后)
    // 视频BV号: BV1...
    private static final Pattern bvidPattern = Pattern.compile("BV([A-Za-z0-9]{10})");
    // 视频AV号: av12345
    private static final Pattern aidPattern = Pattern.compile("av(\\d+)");
    // 番剧单集: ep12345
    private static final Pattern epidPattern = Pattern.compile("ep(\\d+)");
    // 番剧系列: ss12345
    private static final Pattern ssidPattern = Pattern.compile("ss(\\d+)");
    // 剧集信息: md12345
    private static final Pattern mdidPattern = Pattern.compile("md(\\d+)");
    // 直播间: live.bilibili.com/12345
    private static final Pattern roomPattern = Pattern.compile("live\\.bilibili\\.com/(?:blanc/|h5/)?(\\d+)");
    // 专栏/文章: cv12345
    private static final Pattern cvidPattern = Pattern.compile("(?:/read/(?:cv|mobile|native)(?:/|\\?id=)?|^cv)(\\d+)");
    // 带type=2的动态 (分享卡片): t.bilibili.com/123?type=2
    private static final Pattern dynamic2Pattern = Pattern.compile("(?:t|m)\\.bilibili\\.com/(\\d+)\\?.*(?:&|&amp;)type=2");
    // 普通动态: t.bilibili.com/123 或 t.bilibili.com/opus/123
    private static final Pattern dynamicPattern = Pattern.compile("(?:t|m)\\.bilibili\\.com/(?:opus/)?(\\d+)");




    /**
     * 根据输入文本判断并返回 API url 或 web api url 与 type 标识
     * 返回格式：String[] {type, apiUrl, originalIdOrNull}
     * type: "video","bangumi","live","article","dynamic", or null
     */
    public static String[] extract(String text) {
        try {
            Matcher m;

            // 视频 (BV优先级高于AV)
            m = bvidPattern.matcher(text);
            if (m.find()) {
                String bvid = m.group(0); // group(0)是整个匹配, e.g., "BV1..."
                String api = "https://api.bilibili.com/x/web-interface/view?bvid=" + URLEncoder.encode(bvid, StandardCharsets.UTF_8);
                return new String[]{"video", api, null};
            }
            m = aidPattern.matcher(text);
            if (m.find()) {
                String aid = m.group(1); // group(1)是数字部分
                String api = "https://api.bilibili.com/x/web-interface/view?aid=" + URLEncoder.encode(aid, StandardCharsets.UTF_8);
                return new String[]{"video", api, null};
            }

            // 番剧/剧集
            m = epidPattern.matcher(text);
            if (m.find()) {
                String ep = m.group(1);
                String api = "https://api.bilibili.com/pgc/view/web/season?ep_id=" + ep;
                return new String[]{"bangumi", api, null};
            }
            m = ssidPattern.matcher(text);
            if (m.find()) {
                String ss = m.group(1);
                String api = "https://api.bilibili.com/pgc/view/web/season?season_id=" + ss;
                return new String[]{"bangumi", api, null};
            }
            m = mdidPattern.matcher(text);
            if (m.find()) {
                String md = m.group(1);
                String api = "https://api.bilibili.com/pgc/review/user?media_id=" + md;
                return new String[]{"bangumi", api, null};
            }

            // 直播
            m = roomPattern.matcher(text);
            if (m.find()) {
                String room = m.group(1);
                String api = "https://api.live.bilibili.com/xlive/web-room/v1/index/getInfoByRoom?room_id=" + room;
                return new String[]{"live", api, null};
            }

            // 文章
            m = cvidPattern.matcher(text);
            if (m.find()) {
                String cv = m.group(1);
                String api = "https://api.bilibili.com/x/article/viewinfo?id=" + cv + "&mobi_app=pc&from=web";
                return new String[]{"article", api, cv};
            }

            // 动态 (更具体的type=2优先)
            m = dynamic2Pattern.matcher(text);
            if (m.find()) {
                String rid = m.group(1);
                String api = "https://api.bilibili.com/x/polymer/web-dynamic/v1/detail?rid=" + rid + "&type=2";
                return new String[]{"dynamic", api, null};
            }
            m = dynamicPattern.matcher(text);
            if (m.find()) {
                String id = m.group(1);
                String api = "https://api.bilibili.com/x/polymer/web-dynamic/v1/detail?id=" + id;
                return new String[]{"dynamic", api, null};
            }
        } catch (Exception e) {
            // 在日志中记录异常会是更好的做法，但根据原逻辑，这里保持静默失败
            // logger.error("BiliUtils.extract failed", e);
        }
        return new String[]{null, null, null};
    }

    public static String resizeImage(String src, String imagesSize, String coverImagesSize, boolean isCover) {
        if (src == null || src.isEmpty()) {
            return src;
        }
        if (isCover && coverImagesSize != null && !coverImagesSize.isEmpty()) {
            String imgType = src.length() >= 3 ? src.substring(src.length() - 3) : "";
            return src + "@" + coverImagesSize + "." + imgType;
        }
        if (imagesSize != null && !imagesSize.isEmpty()) {
            String imgType = src.length() >= 3 ? src.substring(src.length() - 3) : "";
            return src + "@" + imagesSize + "." + imgType;
        }
        return src;
    }

    // helper to convert big numbers like Python handle_num
    public static String handleNum(long num) {
        if (num > 10000) {
            double v = (double) num / 10000.0;
            return String.format("%.2f万", v);
        }
        return String.valueOf(num);
    }


}
