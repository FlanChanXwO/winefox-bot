package com.github.winefoxbot.plugins.linkresolver.service.impl;

import cn.hutool.core.date.DateUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.winefoxbot.core.context.BotContext;
import com.github.winefoxbot.plugins.linkresolver.config.BilibiliPluginConfig;
import com.github.winefoxbot.plugins.linkresolver.service.LinkResolverService;
import com.github.winefoxbot.plugins.linkresolver.utils.BiliUtils;
import com.github.winefoxbot.plugins.linkresolver.utils.CardGenerator;
import com.mikuac.shiro.common.utils.MsgUtils;
import com.mikuac.shiro.core.Bot;
import com.mikuac.shiro.dto.event.message.GroupMessageEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.apache.logging.log4j.util.Strings;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class BilibiliLinkResolver implements LinkResolverService {

    private final ObjectMapper mapper;
    private final OkHttpClient client;

    // 移除了原正则中未被捕获组1包含的部分，确保只匹配有效的 URL 或 ID
    private static final String REGEX_STR =
            "https?://(?:www\\.bilibili\\.com/(?:video|read|bangumi)/[a-zA-Z0-9]+(?:\\?\\S*)?|b23\\.tv/[a-zA-Z0-9]+(?:\\?\\S*)?|live\\.bilibili\\.com/\\d+|t\\.bilibili\\.com/\\d+)" +
                    "|\\b(?:av|cv)\\d{1,12}\\b|\\bBV[a-zA-Z0-9]{10}\\b|m\\.q\\.qq\\.com/a/s/[a-zA-Z0-9]+";

    private static final Pattern REGEX = Pattern.compile(REGEX_STR, Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    @Override
    public Pattern getRegex() {
        return REGEX;
    }

    @Override
    public void resolve(Bot bot, GroupMessageEvent event, String urlToParse) {
        try {
            // 处理短链接
            if (urlToParse.toLowerCase().contains("b23.tv") || urlToParse.toLowerCase().contains("bili23.cn") || urlToParse.toLowerCase().contains("m.q.qq.com")) {
                try {
                    // 如果是小程序链接，需要补充协议头
                    if (urlToParse.startsWith("m.q.qq.com")) {
                        urlToParse = "https://" + urlToParse;
                    }
                    String expanded = expandShortLink(urlToParse);
                    if (!expanded.isEmpty()) {
                        urlToParse = expanded;
                    }
                } catch (Exception e) {
                    log.debug("短链接展开失败: {}", e.getMessage());
                }
            }

            // 提取信息
            String[] extracted = BiliUtils.extract(urlToParse);
            String type = extracted[0];
            String api = extracted[1];
            String cvid = extracted[2];

            if (type == null || api == null) {
                return;
            }

            processAnalysis(bot, event.getGroupId(), type, api, cvid);

        } catch (Exception ex) {
            log.error("B站链接解析出错", ex);
        }
    }

    private void processAnalysis(Bot bot, long groupId, String type, String api, String cvid) {
        BilibiliPluginConfig config = (BilibiliPluginConfig) BotContext.CURRENT_PLUGIN_CONFIN.get();
        if (config == null) {
            log.warn("BilibiliPluginConfig not found in context");
            return;
        }

        // 如果开启了图片模式且是视频类型
        if (Boolean.TRUE.equals(config.getSendImage()) && "video".equals(type)) {
            try {
                JsonNode root = httpGetJson(api, config);
                JsonNode data = root.path("data");
                if (!data.isMissingNode()) {
                    String title = data.path("title").asText();
                    String cover = data.path("pic").asText();

                    JsonNode owner = data.path("owner");
                    String upName = owner.path("name").asText();
                    String upFace = owner.path("face").asText();

                    JsonNode stat = data.path("stat");
                    String plays = BiliUtils.handleNum(stat.path("view").asLong());
                    String danmaku = BiliUtils.handleNum(stat.path("danmaku").asLong());
                    String like = BiliUtils.handleNum(stat.path("like").asLong());
                    String coin = BiliUtils.handleNum(stat.path("coin").asLong());
                    String favorite = BiliUtils.handleNum(stat.path("favorite").asLong());
                    String share = BiliUtils.handleNum(stat.path("share").asLong());

                    long pubDate = data.path("pubdate").asLong();
                    String dateStr = DateUtil.date(pubDate * 1000).toString();

                    String summary = data.path("desc").asText();
                    // 截断过长的简介
                    if (summary.length() > 100) {
                        summary = summary.substring(0, 100) + "...";
                    }

                    File cardFile = CardGenerator.generateBilibiliCard(
                            title, cover, upName, upFace, dateStr,
                            plays, danmaku, like, coin, favorite, share,
                            summary, config.getTmpPath()
                    );

                    if (cardFile != null && cardFile.exists()) {
                        bot.sendGroupMsg(groupId, MsgUtils.builder().img(cardFile.toPath().toUri().toString()).build(), false);
                    }
                }
            } catch (Exception e) {
                log.error("生成样式卡片失败", e);
            }
        }

        // 视频文件下载逻辑
        if (Boolean.TRUE.equals(config.getAnalysisVideoSend()) && "video".equals(type)) {
            handleVideoDownloadAndSend(bot, groupId, api, config);
        }
    }

    private void handleVideoDownloadAndSend(Bot bot, long groupId, String api, BilibiliPluginConfig config) {
        try {
            File file = downloadVideo(api, config);
            if (file != null) {
                log.info("下载到视频，准备发送: {}", file.getAbsolutePath());
                String videoMsg = MsgUtils.builder()
                        .video("file:///" + file.getAbsolutePath(), Strings.EMPTY)
                        .build();
                bot.sendGroupMsg(groupId, videoMsg, false);

                if (file.exists()) {
                    if (!file.delete()) {
                        file.deleteOnExit();
                    }
                }
            }
        } catch (Exception e) {
            log.error("视频发送失败", e);
        }
    }

    private File downloadVideo(String apiUrl, BilibiliPluginConfig config) {
        try {
            JsonNode root = httpGetJson(apiUrl, config);
            JsonNode data = root.path("data");
            if (data.isMissingNode() || data.isNull()) return null;
            long duration = data.get("duration").asLong();
            long cid = data.get("cid").asLong();

            if (duration <= config.getDurationSecLimit()) {
                String bvid = data.get("bvid").asText();
                return downloadBiliVideo(bvid, cid, duration, config);
            }
        } catch (Exception e) {
            log.error("下载视频失败 apiUrl=" + apiUrl, e);
        }
        return null;
    }

    private JsonNode httpGetJson(String url, BilibiliPluginConfig config) throws IOException {
        Request request = buildHttpRequest(url, config);
        try (Response resp = client.newCall(request).execute()) {
            if (!resp.isSuccessful()) throw new IOException("HTTP error " + resp.code() + " for " + url);
            String body = resp.body().string();
            return mapper.readTree(body);
        }
    }

    private Request buildHttpRequest(String url, BilibiliPluginConfig config) {
        return new Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 WineFoxBot")
                .header("Referer", "https://www.bilibili.com/")
                .header("Cookie", config.getCookie())
                .build();
    }

    private String expandShortLink(String shortUrl) throws IOException {
        Request request = new Request.Builder()
                .url(shortUrl)
                .header("User-Agent", "Mozilla/5.0 WineFoxBot")
                .build();
        try (Response resp = client.newCall(request).execute()) {
            HttpUrl finalUrl = resp.request().url();
            return finalUrl.toString();
        }
    }

    private File downloadBiliVideo(String bvid, long cid, long durationSec, BilibiliPluginConfig config) throws Exception {
        if (config.getDurationSecLimit() > 0 && durationSec > config.getDurationSecLimit()) {
            return null;
        }

        String url = "https://api.bilibili.com/x/player/playurl?bvid=" + bvid + "&cid=" + cid + "&qn=80&fnval=16";
        JsonNode root = httpGetJson(url, config).get("data");
        JsonNode dash = root.get("dash");
        String videoUrl = dash.get("video").get(0).get("baseUrl").asText();
        String audioUrl = dash.get("audio").get(0).get("baseUrl").asText();

        File tempDir = new File(config.getTmpPath());
        if (!tempDir.exists()) tempDir.mkdirs();

        File videoFile = new File(tempDir, bvid + "_v.mp4");
        File audioFile = new File(tempDir, bvid + "_a.mp3");
        File outputFile = new File(tempDir, bvid + ".mp4");

        downloadResource(videoUrl, videoFile, config);
        downloadResource(audioUrl, audioFile, config);
        mergeAv(videoFile, audioFile, outputFile);
        return outputFile;
    }

    private void downloadResource(String url, File out, BilibiliPluginConfig config) throws IOException {
        Request req = buildHttpRequest(url, config);
        try (Response resp = client.newCall(req).execute()) {
            if (!resp.isSuccessful()) throw new IOException("HTTP " + resp.code());
            try (var in = resp.body().byteStream();
                 var outStream = new FileOutputStream(out)) {
                in.transferTo(outStream);
            }
        }
    }

    private void mergeAv(File video, File audio, File output) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(
                "ffmpeg", "-y",
                "-i", video.getAbsolutePath(),
                "-i", audio.getAbsolutePath(),
                "-c:v", "copy",
                "-c:a", "aac",
                output.getAbsolutePath()
        );
        pb.inheritIO();
        Process process = pb.start();
        process.waitFor();
        video.delete();
        audio.delete();
    }
}
