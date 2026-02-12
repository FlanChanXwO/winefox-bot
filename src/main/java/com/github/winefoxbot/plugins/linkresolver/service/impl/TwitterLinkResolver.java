package com.github.winefoxbot.plugins.linkresolver.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.winefoxbot.core.context.BotContext;
import com.github.winefoxbot.core.service.file.FileStorageService;
import com.github.winefoxbot.plugins.linkresolver.config.LinkResolverPluginConfig;
import com.github.winefoxbot.plugins.linkresolver.constant.LinkResolverConstants;
import com.github.winefoxbot.plugins.linkresolver.service.LinkResolverService;
import com.github.winefoxbot.plugins.linkresolver.util.CardGenerator;
import com.mikuac.shiro.common.utils.MsgUtils;
import com.mikuac.shiro.common.utils.ShiroUtils;
import com.mikuac.shiro.core.Bot;
import com.mikuac.shiro.dto.event.message.GroupMessageEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.apache.logging.log4j.util.Strings;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.StandardCopyOption;
import java.nio.file.*;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class TwitterLinkResolver implements LinkResolverService {

    private static final String REGEX_STR = "(https?://)?(www\\.)?(twitter\\.com|x\\.com)/\\w+/status/\\d+";
    private static final Pattern REGEX = Pattern.compile(REGEX_STR, Pattern.CASE_INSENSITIVE);
    private static final DateTimeFormatter TWITTER_DATE_FMT = DateTimeFormatter.ofPattern("EEE MMM dd HH:mm:ss Z yyyy", Locale.ENGLISH);
    private static final DateTimeFormatter TARGET_DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final OkHttpClient httpClient;
    private final ObjectMapper mapper;
    private final CardGenerator cardGenerator;
    private final FileStorageService fileStorageService;

    @Override
    public Pattern getRegex() {
        return REGEX;
    }

    @Override
    public String getCanonicalId(String url) {
        Pattern idPattern = Pattern.compile("/status/(\\d+)");
        Matcher matcher = idPattern.matcher(url);
        return matcher.find() ? matcher.group(1) : url;
    }

    @Override
    public void resolve(Bot bot, GroupMessageEvent event, String match) {
        try {
            LinkResolverPluginConfig config = (LinkResolverPluginConfig) BotContext.CURRENT_PLUGIN_CONFIN.get();

            String tweetId = getCanonicalId(match);
            JsonNode responseNode = requestFxTwitterApi(tweetId);

            if (responseNode == null || responseNode.path("code").asInt() != 200) {
                return;
            }

            JsonNode tweet = responseNode.path("tweet");
            boolean isSensitive = tweet.path("possibly_sensitive").asBoolean(false);
            String authorName = tweet.path("author").path("name").asText("Unknown");
            String authorHandle = tweet.path("author").path("screen_name").asText("unknown");

            List<String> cardImageUrls = new ArrayList<>();
            List<String> photoUrls = new ArrayList<>();
            List<String> gifUrls = new ArrayList<>();
            String videoUrl = null;
            String videoThumbnailUrl = null;
            double firstImageAspectRatio = 0.0;
            JsonNode mediaNode = tweet.path("media");

            if (mediaNode.has("all") && mediaNode.path("all").isArray()) {
                int index = 0;
                for (JsonNode mediaItem : mediaNode.path("all")) {
                    if (index == 0) {
                        double w = mediaItem.path("width").asDouble(0);
                        double h = mediaItem.path("height").asDouble(0);
                        if (w > 0 && h > 0) firstImageAspectRatio = w / h;
                    }
                    String type = mediaItem.path("type").asText();
                    String url = mediaItem.path("url").asText();
                    if ("photo".equals(type)) {
                        cardImageUrls.add(url);
                        photoUrls.add(url);
                    } else if ("gif".equals(type)) {
                        cardImageUrls.add(mediaItem.path("thumbnail_url").asText());
                        gifUrls.add(url);
                    } else if ("video".equals(type) && videoUrl == null) {
                        videoUrl = url;
                        videoThumbnailUrl = mediaItem.path("thumbnail_url").asText();
                    }
                    index++;
                }
                if (videoThumbnailUrl != null) {
                    cardImageUrls.add(0, videoThumbnailUrl);
                }
            } else {
                // Fallback for older API structure
                if (mediaNode.has("photos")) {
                    for (JsonNode photo : mediaNode.path("photos")) {
                        String url = photo.path("url").asText();
                        cardImageUrls.add(url);
                        photoUrls.add(url);
                    }
                }
                if (mediaNode.has("videos")) {
                    JsonNode videos = mediaNode.path("videos");
                    if (videos.isArray() && !videos.isEmpty()) {
                        videoUrl = videos.get(0).path("url").asText();
                        String thumbnailUrl = videos.get(0).path("thumbnail_url").asText();
                        if (thumbnailUrl != null && !thumbnailUrl.isEmpty()) {
                            cardImageUrls.add(0, thumbnailUrl);
                        }
                    }
                }
            }

            if (config.getSendCard()) {
                String rawText = tweet.path("text").asText();

                String avatarUrl = tweet.path("author").path("avatar_url").asText("");
                if (avatarUrl.contains("_normal")) {
                    avatarUrl = avatarUrl.replace("_normal", "");
                }

                String dateStr = tweet.path("created_at").asText();
                try {
                    ZonedDateTime zdt = ZonedDateTime.parse(dateStr, TWITTER_DATE_FMT);
                    dateStr = zdt.format(TARGET_DATE_FMT);
                } catch (Exception ignored) {
                }

                List<CardGenerator.CardStatistic> stats = new ArrayList<>();
                stats.add(new CardGenerator.CardStatistic("share_twitter.png", formatNum(tweet.path("retweets").asLong(0))));
                stats.add(new CardGenerator.CardStatistic("favourite_twitter.png", formatNum(tweet.path("likes").asLong(0))));
                long views = tweet.path("views").asLong(0);
                if (views > 0) {
                    stats.add(new CardGenerator.CardStatistic("views_twitter.png", formatNum(views)));
                }

                Path cardPath = cardGenerator.generateCard(
                        authorName, "@" + authorHandle, avatarUrl,null, rawText, cardImageUrls, dateStr,
                        stats, "twitter", isSensitive, firstImageAspectRatio, videoUrl != null, "twitter-card-" + tweetId + ".png"
                );

                if (cardPath != null) {
                    bot.sendGroupMsg(event.getGroupId(), MsgUtils.builder().img(cardPath.toAbsolutePath().toString()).build(), false);
                }
            }

            if (config.getSendResource() && !isSensitive) {
                if (!photoUrls.isEmpty()) {
                    List<String> forwardMsgs = new ArrayList<>();
                    int imageIndex = 0;
                    for (String imageUrl : photoUrls) {
                        String cacheKey = "twitter-image-" + tweetId + "-" + imageIndex;
                        Path localImagePath = downloadAndCacheResource(imageUrl, cacheKey);
                        if (localImagePath != null) {
                            forwardMsgs.add(MsgUtils.builder().img(localImagePath.toUri().toString()).build());
                        }
                        imageIndex++;
                    }
                    if (!forwardMsgs.isEmpty()) {
                        bot.sendGroupForwardMsg(event.getGroupId(), ShiroUtils.generateForwardMsg(bot, forwardMsgs));
                    }
                }

                if (!gifUrls.isEmpty()) {
                    int gifIndex = 0;
                    for (String gifUrl : gifUrls) {
                        String cacheKey = "twitter-gif-converted-" + tweetId + "-" + gifIndex + ".gif";
                        Path localGifPath = downloadAndConvertAndCacheGif(gifUrl, cacheKey);
                        if (localGifPath != null) {
                            bot.sendGroupMsg(event.getGroupId(), MsgUtils.builder().img(localGifPath.toUri().toString()).build(), false);
                        }
                        gifIndex++;
                    }
                }

                if (videoUrl != null) {
                    String cacheKey = "twitter-video-" + tweetId + ".mp4";
                    Path videoPath = downloadAndCacheResource(videoUrl, cacheKey);
                    if (videoPath != null) {
                        bot.sendGroupMsg(event.getGroupId(), MsgUtils.builder().video(videoPath.toUri().toString(), Strings.EMPTY).build(), false);
                    }
                }
            }

        } catch (Exception e) {
            log.error("Twitter 解析异常", e);
        }
    }

    private String formatNum(long num) {
        if (num >= 10000) {
            return String.format("%.1f万", num / 10000.0);
        }
        return String.valueOf(num);
    }

    private JsonNode requestFxTwitterApi(String tweetId) throws IOException {
        Request request = new Request.Builder()
                .url("https://api.fxtwitter.com/status/" + tweetId)
                .get()
                .addHeader("User-Agent", "WineFoxBot/LinkResolver (Java)")
                .build();
        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) return null;
            return mapper.readTree(Objects.requireNonNull(response.body()).string());
        }
    }

    private Path downloadAndCacheResource(String url, String cacheKey) {
        try {
            Path cachedPath = fileStorageService.getFilePathByCacheKey(cacheKey);
            if (cachedPath != null && Files.exists(cachedPath)) {
                log.debug("Found resource in cache: {}", cacheKey);
                return cachedPath;
            }

            log.debug("Downloading resource for cache: {}", url);
            Request request = new Request.Builder().url(url).build();
            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful() || response.body() == null) {
                    log.error("Failed to download resource from url: {}. Response code: {}", url, response.code());
                    return null;
                }
                try (InputStream in = response.body().byteStream()) {
                    return fileStorageService.saveFileByCacheKey(cacheKey, in, LinkResolverConstants.RESOURCE_CACHE_DURATION);
                }
            }
        } catch (IOException e) {
            log.error("Error downloading or caching resource: {}", url, e);
            return null;
        }
    }

    private Path downloadAndConvertAndCacheGif(String mp4Url, String finalCacheKey) {
        try {
            // 1. Check cache for final GIF
            Path cachedGifPath = fileStorageService.getFilePathByCacheKey(finalCacheKey);
            if (cachedGifPath != null && Files.exists(cachedGifPath)) {
                log.debug("Found converted GIF in cache: {}", finalCacheKey);
                return cachedGifPath;
            }

            // 2. Download MP4 to a temp file
            Path tempMp4 = null;
            Path tempGif = null;
            try {
                tempMp4 = Files.createTempFile("twitter-gif-", ".mp4");
                log.debug("Downloading GIF (as MP4) for conversion: {}", mp4Url);
                Request request = new Request.Builder().url(mp4Url).build();
                try (Response response = httpClient.newCall(request).execute()) {
                    if (!response.isSuccessful() || response.body() == null) {
                        log.error("Failed to download resource from url: {}. Response code: {}", mp4Url, response.code());
                        return null;
                    }
                    try (InputStream in = response.body().byteStream()) {
                        Files.copy(in, tempMp4, StandardCopyOption.REPLACE_EXISTING);
                    }
                }

                // 3. Convert MP4 to GIF
                tempGif = Files.createTempFile("twitter-gif-converted-", ".gif");
                convertMp4ToGif(tempMp4, tempGif);

                // 4. Cache the converted GIF
                try (InputStream gifStream = Files.newInputStream(tempGif)) {
                    return fileStorageService.saveFileByCacheKey(finalCacheKey, gifStream, LinkResolverConstants.RESOURCE_CACHE_DURATION);
                }

            } finally {
                // 5. Clean up temp files
                if (tempMp4 != null) Files.deleteIfExists(tempMp4);
                if (tempGif != null) Files.deleteIfExists(tempGif);
            }
        } catch (Exception e) {
            log.error("Error downloading or converting GIF for URL: {}", mp4Url, e);
            return null;
        }
    }

    private void convertMp4ToGif(Path inputMp4, Path outputGif) throws IOException, InterruptedException {
        log.debug("Converting {} to {}", inputMp4, outputGif);
        ProcessBuilder pb = new ProcessBuilder(
                "ffmpeg", "-y", "-i", inputMp4.toAbsolutePath().toString(),
                "-vf", "fps=15,scale=480:-1:flags=lanczos,split[s0][s1];[s0]palettegen[p];[s1][p]paletteuse",
                "-loop", "0", outputGif.toAbsolutePath().toString()
        );
        Process process = pb.start();
        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new IOException("ffmpeg process exited with code " + exitCode);
        }
        log.debug("Successfully converted {} to {}", inputMp4, outputGif);
    }
}
