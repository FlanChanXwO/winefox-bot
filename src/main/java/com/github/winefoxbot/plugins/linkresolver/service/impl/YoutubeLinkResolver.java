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
    private static final Pattern AVATAR_REGEX = Pattern.compile("<meta property=\"og:image\" content=\"(.*?)\">");


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
                String apiUrl = "https://www.youtube.com/oembed?url=https://www.youtube.com/watch?v=" + videoId + "&format=json";
                JsonNode data = httpGetJson(apiUrl);

                if (data != null) {
                    String title = data.path("title").asText();
                    String authorName = data.path("author_name").asText();
                    String authorUrl = data.path("author_url").asText();
                    String thumbnailUrl = data.path("thumbnail_url").asText();
                    String authorAvatarUrl = getAuthorAvatarUrl(authorUrl);

                    List<String> imageUrls = Collections.singletonList(thumbnailUrl);

                    Path cardPath = cardGenerator.generateCard(
                            authorName, "", authorAvatarUrl, title, imageUrls,
                            null, new ArrayList<>(), "youtube", false, 16.0 / 9.0, true, "youtube-card-" + videoId + ".png");

                    if (cardPath != null) {
                        bot.sendGroupMsg(event.getGroupId(), MsgUtils.builder().img(cardPath.toAbsolutePath().toString()).build(), false);
                    }
                }
            } catch (Exception e) {
                log.error("Failed to resolve YouTube link and send card", e);
            }
        }
    }

    private boolean downloadAndSendVideo(Bot bot, GroupMessageEvent event, String videoId, LinkResolverPluginConfig config) {
        Path tempVideoFile = null;
        Path tempThumbnailFile = null;
        try {
            String videoUrl = "https://www.youtube.com/watch?v=" + videoId;
            ProcessBuilder infoProcessBuilder = new ProcessBuilder("yt-dlp", "--dump-json", videoUrl);
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
            ProcessBuilder downloadProcessBuilder = new ProcessBuilder(
                    "yt-dlp",
                    "-f", "bv*[ext=mp4]+ba[ext=m4a]/b[ext=mp4]/best",
                    "-o", tempVideoFile.toAbsolutePath().toString(),
                    videoUrl
            );
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
            } catch (IOException e) {
                log.error("Failed to delete temporary files for video {}", videoId, e);
            }
        }
    }

    private String getAuthorAvatarUrl(String authorUrl) {
        if (authorUrl == null || authorUrl.isEmpty()) {
            return null;
        }
        try {
            Request request = new Request.Builder().url(authorUrl).header("User-Agent", "Mozilla/5.0 WineFoxBot").build();
            try (Response response = client.newCall(request).execute()) {
                if (response.isSuccessful() && response.body() != null) {
                    String html = response.body().string();
                    Matcher matcher = AVATAR_REGEX.matcher(html);
                    if (matcher.find()) {
                        return matcher.group(1);
                    }
                }
            }
        } catch (IOException e) {
            log.error("Failed to fetch author avatar for url: {}", authorUrl, e);
        }
        return null;
    }

    private JsonNode httpGetJson(String url) throws IOException {
        Request request = new Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 WineFoxBot")
                .build();
        try (Response resp = client.newCall(request).execute()) {
            if (!resp.isSuccessful()) {
                throw new IOException("HTTP error " + resp.code() + " for " + url);
            }
            String body = resp.body().string();
            return mapper.readTree(body);
        }
    }
}
