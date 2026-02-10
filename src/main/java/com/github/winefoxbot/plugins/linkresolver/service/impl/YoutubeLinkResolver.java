package com.github.winefoxbot.plugins.linkresolver.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.winefoxbot.core.context.BotContext;
import com.github.winefoxbot.plugins.linkresolver.config.LinkResolverPluginConfig;
import com.github.winefoxbot.plugins.linkresolver.service.LinkResolverService;
import com.github.winefoxbot.plugins.linkresolver.util.CardGenerator;
import com.mikuac.shiro.common.utils.MsgUtils;
import com.mikuac.shiro.core.Bot;
import com.mikuac.shiro.dto.event.message.GroupMessageEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class YoutubeLinkResolver implements LinkResolverService {

    private final ObjectMapper mapper;
    private final OkHttpClient client;
    private final CardGenerator cardGenerator;

    private static final String REGEX_STR = "(?:https?://)?(?:www\\.)?(?:m\\.)?(?:youtube\\.com|youtu\\.be)/(?:watch\\?v=|v/|embed/|shorts/)?([a-zA-Z0-9_-]{11})";
    private static final Pattern REGEX = Pattern.compile(REGEX_STR);


    @Override
    public Pattern getRegex() {
        return REGEX;
    }

    @Override
    public String getCanonicalId(String url) {
        Matcher matcher = REGEX.matcher(url);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    @Override
    public void resolve(Bot bot, GroupMessageEvent event, String url) {
        LinkResolverPluginConfig config = (LinkResolverPluginConfig) BotContext.CURRENT_PLUGIN_CONFIN.get();
        if (config == null) {
            return;
        }

        String videoId = getCanonicalId(url);
        if (videoId == null) {
            return;
        }

        boolean resourceSent = false;
        if (Boolean.TRUE.equals(config.getSendResource())) {
            try {
                // Assuming yt-dlp is in the system's PATH
                resourceSent = downloadAndSendVideo(bot, event, videoId, config);
            } catch (Exception e) {
                log.error("Error attempting to send YouTube resource for videoId: {}", videoId, e);
            }
        }

        // If resource was not sent (e.g. too long, or disabled), and cards are enabled, send a card.
        if (!resourceSent && Boolean.TRUE.equals(config.getSendCard())) {
            try {
                JsonNode videoInfo = getVideoInfoFromPlayerApi(videoId);

                if (videoInfo != null) {
                    JsonNode details = videoInfo.path("videoDetails");
                    JsonNode microformat = videoInfo.path("microformat").path("playerMicroformatRenderer");

                    String title = details.path("title").asText("未知标题");
                    String authorName = details.path("author").asText("未知作者");

                    JsonNode thumbnails = details.path("thumbnail").path("thumbnails");
                    String thumbnailUrl = "";
                    if (thumbnails.isArray() && !thumbnails.isEmpty()) {
                        thumbnailUrl = thumbnails.get(thumbnails.size() - 1).path("url").asText();
                    }

                    JsonNode authorThumbnails = microformat.path("thumbnail").path("thumbnails");
                    String authorAvatarUrl = "";
                    if (authorThumbnails.isArray() && !authorThumbnails.isEmpty()) {
                        authorAvatarUrl = authorThumbnails.get(authorThumbnails.size() - 1).path("url").asText();
                    }
                    List<String> imageUrls = Collections.singletonList(thumbnailUrl);

                    Path cardPath = cardGenerator.generateCard(
                            authorName, "", authorAvatarUrl, title, imageUrls,
                            null, new ArrayList<>(), "youtube", false, 16.0 / 9.0, true, "youtube-card-" + videoId + ".png");

                    if (cardPath != null) bot.sendGroupMsg(event.getGroupId(), MsgUtils.builder().img(cardPath.toAbsolutePath().toString()).build(), false);
                }
            } catch (Exception e) {
                log.error("Failed to resolve YouTube link and send card", e);
            }
        }
    }

    private boolean downloadAndSendVideo(Bot bot, GroupMessageEvent event, String videoId, LinkResolverPluginConfig config) {
        Path tempVideoFile = null;
        Path tempThumbnailFile = null;
        Path tempCookieFile = null;
        try {
            String videoUrl = "https://www.youtube.com/watch?v=" + videoId;

            List<String> commandArgs = new ArrayList<>();
            commandArgs.add(config.getYtDlpPath());

            // 从OkHttpClient获取代理并应用到yt-dlp
            Proxy proxy = client.proxy();
            if (proxy != null && proxy.type() != java.net.Proxy.Type.DIRECT) {
                if (proxy.address() instanceof InetSocketAddress address) {
                    String host = address.getHostString();
                    int port = address.getPort();
                    String proxyUrl;
                    if (proxy.type() == java.net.Proxy.Type.SOCKS) {
                        proxyUrl = "socks5://" + host + ":" + port;
                    } else { // 默认为 HTTP 代理
                        proxyUrl = "http://" + host + ":" + port;
                    }
                    commandArgs.add("--proxy");
                    commandArgs.add(proxyUrl);
                    log.debug("Using proxy {} for yt-dlp from OkHttpClient configuration.", proxyUrl);
                }
            }

            String cookieContent = config.getYoutubeCookie();
            if (cookieContent != null && !cookieContent.trim().isEmpty()) {
                tempCookieFile = Files.createTempFile("youtube-cookies-", ".txt");
                Files.write(tempCookieFile, cookieContent.getBytes(StandardCharsets.UTF_8));
                commandArgs.add("--cookies");
                commandArgs.add(tempCookieFile.toAbsolutePath().toString());
                log.debug("Using YouTube cookies from config for videoId: {}", videoId);
            }

            List<String> infoCommand = new ArrayList<>(commandArgs);
            infoCommand.add("--dump-json");
            infoCommand.add(videoUrl);
            ProcessBuilder infoProcessBuilder = new ProcessBuilder(infoCommand);
            infoProcessBuilder.redirectErrorStream(true);
            Process infoProcess = infoProcessBuilder.start();

            String jsonOutput;
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(infoProcess.getInputStream()))) {
                jsonOutput = reader.lines().collect(Collectors.joining("\n"));
            }

            if (infoProcess.waitFor() != 0) {
                log.error("yt-dlp failed to get video info for {}. Output: {}", videoUrl, jsonOutput);
                return false;
            }

            JsonNode videoInfo = mapper.readTree(jsonOutput);
            long duration = videoInfo.path("duration").asLong();
            String thumbnailUrl = videoInfo.path("thumbnail").asText();

            Long durationLimit = config.getDurationSecLimit();
            if (durationLimit != null && durationLimit > 0 && duration > durationLimit) {
                log.info("Video {} duration ({}) exceeds limit ({}), skipping download.", videoId, duration, durationLimit);
                return false;
            }

            if (thumbnailUrl == null || thumbnailUrl.isEmpty()) {
                log.warn("No thumbnail URL found for video {}. Aborting video send.", videoId);
                return false;
            }

            Request request = new Request.Builder().url(thumbnailUrl).build();
            try (Response response = client.newCall(request).execute()) {
                if (!response.isSuccessful() || response.body() == null) {
                    throw new IOException("Failed to download thumbnail from " + thumbnailUrl);
                }
                tempThumbnailFile = Files.createTempFile("youtube-thumb-" + videoId, ".jpg");
                Files.copy(response.body().byteStream(), tempThumbnailFile, StandardCopyOption.REPLACE_EXISTING);
            }

            tempVideoFile = Files.createTempFile("youtube-" + videoId, ".mp4");
            List<String> downloadCommand = new ArrayList<>(commandArgs);
            downloadCommand.add("-f");
            downloadCommand.add("bv*[ext=mp4]+ba[ext=m4a]/b[ext=mp4]/best");
            downloadCommand.add("-o");
            downloadCommand.add(tempVideoFile.toAbsolutePath().toString());
            downloadCommand.add(videoUrl);
            ProcessBuilder downloadProcessBuilder = new ProcessBuilder(downloadCommand);
            downloadProcessBuilder.redirectErrorStream(true);

            log.info("Starting download for video: {}", videoId);
            Process downloadProcess = downloadProcessBuilder.start();

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(downloadProcess.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    log.debug("yt-dlp: {}", line);
                }
            }

            if (!downloadProcess.waitFor(10, TimeUnit.MINUTES) || downloadProcess.exitValue() != 0) {
                log.error("yt-dlp failed to download video {}. Process timed out or exited with error.", videoId);
                return false;
            }
            log.info("Download finished for video: {}", videoId);

            bot.sendGroupMsg(event.getGroupId(), MsgUtils.builder().video(tempVideoFile.toAbsolutePath().toString(), tempThumbnailFile.toAbsolutePath().toString()).build(), false);
            log.info("Sent video {} to group {}", videoId, event.getGroupId());

            return true;

        } catch (Exception e) {
            log.error("Failed to download and send YouTube video", e);
            return false;
        } finally {
            try {
                if (tempVideoFile != null) Files.deleteIfExists(tempVideoFile);
                if (tempThumbnailFile != null) Files.deleteIfExists(tempThumbnailFile);
                if (tempCookieFile != null) Files.deleteIfExists(tempCookieFile);
            } catch (IOException e) {
                log.error("Failed to delete temporary files for video {}", videoId, e);
            }
        }
    }

    private JsonNode getVideoInfoFromPlayerApi(String videoId) throws IOException {
        String url = "https://www.youtube.com/youtubei/v1/player";
        String payload = String.format("{\"videoId\":\"%s\",\"context\":{\"client\":{\"clientName\":\"WEB\",\"clientVersion\":\"2.20240328.01.00\"}}}", videoId);

        Request request = new Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .post(okhttp3.RequestBody.create(payload, okhttp3.MediaType.get("application/json")))
                .build();

        try (Response resp = client.newCall(request).execute()) {
            if (!resp.isSuccessful()) {
                throw new IOException("YouTube Player API request failed with code " + resp.code() + " for videoId " + videoId);
            }
            String body = resp.body().string();
            return mapper.readTree(body);
        }
    }
}
