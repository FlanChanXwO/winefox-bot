package com.github.winefoxbot.plugins.linkresolver.service.impl;

import cn.hutool.core.date.DateUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.winefoxbot.core.context.BotContext;
import com.github.winefoxbot.core.service.file.FileStorageService;
import com.github.winefoxbot.plugins.linkresolver.config.LinkResolverConfig;
import com.github.winefoxbot.plugins.linkresolver.config.LinkResolverPluginConfig;
import com.github.winefoxbot.plugins.linkresolver.constant.LinkResolverConstants;
import com.github.winefoxbot.plugins.linkresolver.service.LinkResolverService;
import com.github.winefoxbot.plugins.linkresolver.util.BiliUtil;
import com.github.winefoxbot.plugins.linkresolver.util.CardGenerator;
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
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class BilibiliLinkResolver implements LinkResolverService {

    private final ObjectMapper mapper;
    private final OkHttpClient client;
    private final LinkResolverConfig linkResolverConfig;
    private final CardGenerator cardGenerator;
    private final FileStorageService fileStorageService;
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
    public String getCanonicalId(String url) {
        try {
            // 处理短链接
            if (url.toLowerCase().contains("b23.tv") || url.toLowerCase().contains("bili23.cn") || url.toLowerCase().contains("m.q.qq.com")) {
                // 如果是小程序链接，需要补充协议头
                if (url.startsWith("m.q.qq.com")) {
                    url = "https://" + url;
                }
                String expanded = expandShortLink(url);
                if (!expanded.isEmpty()) {
                    url = expanded;
                }
            }
        } catch (Exception e) {
            log.debug("短链接展开失败: {}", e.getMessage());
        }

        // 提取信息
        String[] extracted = BiliUtil.extract(url);
        String bvid = extracted[0];
        if ("video".equals(bvid)) {
            return getBvidFromApi(extracted[1]);
        }
        return extracted[2];
    }

    private String getBvidFromApi(String apiUrl) {
        if (apiUrl == null || apiUrl.isEmpty()) {
            return null;
        }
        try {
            JsonNode root = httpGetJson(apiUrl, null);
            JsonNode data = root.path("data");
            if (!data.isMissingNode()) {
                if (data.has("bvid")) {
                    return data.path("bvid").asText();
                } else if (data.has("aid")) {
                    return "av" + data.path("aid").asText();
                }
            }
        } catch (Exception e) {
            log.error("从API获取bvid失败", e);
        }
        return null;
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
            String[] extracted = BiliUtil.extract(urlToParse);
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
        LinkResolverPluginConfig config = (LinkResolverPluginConfig) BotContext.CURRENT_PLUGIN_CONFIN.get();
        if (config == null) {
            log.warn("BilibiliPluginConfig not found in context");
            return;
        }

        if (Boolean.TRUE.equals(config.getSendCard()) && "video".equals(type)) {
            try {
                JsonNode root = httpGetJson(api, config);
                JsonNode data = root.path("data");
                if (!data.isMissingNode()) {
                    String title = data.path("title").asText();
                    String cover = data.path("pic").asText();
                    String upName = data.path("owner").path("name").asText();
                    String upFace = data.path("owner").path("face").asText();
                    long pubDate = data.path("pubdate").asLong();
                    String dateStr = DateUtil.date(pubDate * 1000).toString();
                    String summary = data.path("desc").asText();
                    if (summary.length() > 100) {
                        summary = summary.substring(0, 100) + "...";
                    }

                    JsonNode stat = data.path("stat");
                    List<CardGenerator.CardStatistic> statistics = new ArrayList<>();
                    statistics.add(new CardGenerator.CardStatistic("views.png", BiliUtil.handleNum(stat.path("view").asLong())));
                    statistics.add(new CardGenerator.CardStatistic("barrage.png", BiliUtil.handleNum(stat.path("danmaku").asLong())));
                    statistics.add(new CardGenerator.CardStatistic("like.png", BiliUtil.handleNum(stat.path("like").asLong())));
                    statistics.add(new CardGenerator.CardStatistic("coin.png", BiliUtil.handleNum(stat.path("coin").asLong())));
                    statistics.add(new CardGenerator.CardStatistic("favourite.png", BiliUtil.handleNum(stat.path("favorite").asLong())));
                    statistics.add(new CardGenerator.CardStatistic("share.png", BiliUtil.handleNum(stat.path("share").asLong())));

                    List<String> imageUrls = new ArrayList<>();
                    imageUrls.add(cover);

                    Path cardPath = cardGenerator.generateCard(
                            upName, dateStr, upFace, title + "\n\n" + summary, imageUrls,
                            null, statistics, "bilibili", false, 16.0 / 9.0, true, "bili-card-" + cvid + ".png");

                    if (cardPath != null) {
                        bot.sendGroupMsg(groupId, MsgUtils.builder().img(cardPath.toAbsolutePath().toString()).build(), false);
                    }
                }
            } catch (Exception e) {
                log.error("生成样式卡片失败", e);
            }
        }

        if (Boolean.TRUE.equals(config.getSendResource()) && "video".equals(type)) {
            handleVideoDownloadAndSend(bot, groupId, api, config);
        }
    }

    private void handleVideoDownloadAndSend(Bot bot, long groupId, String api, LinkResolverPluginConfig config) {
        try {
            Path filePath = downloadVideo(api, config);
            if (filePath != null) {
                log.info("下载到视频，准备发送: {}", filePath.toAbsolutePath());
                String videoMsg = MsgUtils.builder()
                        .video(filePath.toUri().toString(), Strings.EMPTY)
                        .build();
                bot.sendGroupMsg(groupId, videoMsg, false);
            }
        } catch (Exception e) {
            log.error("视频发送失败", e);
        }
    }

    private Path downloadVideo(String apiUrl, LinkResolverPluginConfig config) {
        try {
            JsonNode root = httpGetJson(apiUrl, config);
            JsonNode data = root.path("data");
            if (data.isMissingNode() || data.isNull()) return null;
            long duration = data.get("duration").asLong();
            long cid = data.get("cid").asLong();
            String bvid = data.get("bvid").asText();

            if (duration <= config.getDurationSecLimit()) {
                String cacheKey = "bili-video-" + bvid + ".mp4";
                Path cachedPath = fileStorageService.getFilePathByCacheKey(cacheKey);
                if (cachedPath != null && Files.exists(cachedPath)) {
                    log.debug("Found Bilibili video in cache: {}", cacheKey);
                    return cachedPath;
                }

                File mergedVideo = downloadBiliVideo(bvid, cid, duration, config);
                if (mergedVideo != null && mergedVideo.exists()) {
                    try (InputStream is = new FileInputStream(mergedVideo)) {
                        return fileStorageService.saveFileByCacheKey(cacheKey, is, LinkResolverConstants.RESOURCE_CACHE_DURATION);
                    } finally {
                        if (!mergedVideo.delete()) {
                            mergedVideo.deleteOnExit();
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.error("下载视频失败 apiUrl=" + apiUrl, e);
        }
        return null;
    }

    private JsonNode httpGetJson(String url, LinkResolverPluginConfig config) throws IOException {
        Request request = buildHttpRequest(url, config);
        try (Response resp = client.newCall(request).execute()) {
            if (!resp.isSuccessful()) throw new IOException("HTTP error " + resp.code() + " for " + url);
            String body = resp.body().string();
            return mapper.readTree(body);
        }
    }

    private Request buildHttpRequest(String url, LinkResolverPluginConfig config) {
        Request.Builder builder = new Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 WineFoxBot")
                .header("Referer", "https://www.bilibili.com/");
        if (config != null && !config.getBiliBilicookie().isBlank()) {
            builder.header("Cookie", config.getBiliBilicookie());
        }
        return builder.build();
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

    private File downloadBiliVideo(String bvid, long cid, long durationSec, LinkResolverPluginConfig config) throws Exception {
        if (config.getDurationSecLimit() > 0 && durationSec > config.getDurationSecLimit()) {
            return null;
        }

        String url = "https://api.bilibili.com/x/player/playurl?bvid=" + bvid + "&cid=" + cid + "&qn=80&fnval=16";
        JsonNode root = httpGetJson(url, config).get("data");
        JsonNode dash = root.get("dash");
        String videoUrl = dash.get("video").get(0).get("baseUrl").asText();
        String audioUrl = dash.get("audio").get(0).get("baseUrl").asText();

        File tempDir = new File(linkResolverConfig.getTmpPath());
        if (!tempDir.exists()) tempDir.mkdirs();

        File videoFile = new File(tempDir, bvid + "_v.mp4");
        File audioFile = new File(tempDir, bvid + "_a.mp3");
        File outputFile = new File(tempDir, bvid + ".mp4");

        downloadResource(videoUrl, videoFile, config);
        downloadResource(audioUrl, audioFile, config);
        mergeAv(videoFile, audioFile, outputFile);
        return outputFile;
    }

    private void downloadResource(String url, File out, LinkResolverPluginConfig config) throws IOException {
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
