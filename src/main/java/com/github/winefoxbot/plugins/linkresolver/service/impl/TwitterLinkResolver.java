package com.github.winefoxbot.plugins.linkresolver.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.winefoxbot.core.context.BotContext;
import com.github.winefoxbot.plugins.linkresolver.config.BilibiliPluginConfig;
import com.github.winefoxbot.plugins.linkresolver.service.LinkResolverService;
import com.github.winefoxbot.plugins.linkresolver.utils.CardGenerator;
import com.mikuac.shiro.common.utils.MsgUtils;
import com.mikuac.shiro.core.Bot;
import com.mikuac.shiro.dto.event.message.GroupMessageEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class TwitterLinkResolver implements LinkResolverService {

    // 匹配 twitter.com 或 x.com 的链接
    private static final String REGEX_STR = "(https?://)?(www\\.)?(twitter\\.com|x\\.com)/\\w+/status/\\d+";
    private static final Pattern REGEX = Pattern.compile(REGEX_STR, Pattern.CASE_INSENSITIVE);

    private final OkHttpClient httpClient;
    private final ObjectMapper mapper;

    @Override
    public Pattern getRegex() { return REGEX; }

    @Override
    public void resolve(Bot bot, GroupMessageEvent event, String match) {
        BilibiliPluginConfig config = (BilibiliPluginConfig) BotContext.CURRENT_PLUGIN_CONFIN.get();
        if (config == null) {
            log.warn("Config not found");
            return;
        }

        // 使用 fxtwitter API 获取元数据
        // 原始链接: https://x.com/username/status/123456
        // API 链接: https://api.fxtwitter.com/username/status/123456
        String apiUrl = match.replace("x.com", "api.fxtwitter.com")
                             .replace("twitter.com", "api.fxtwitter.com");
        
        // 确保协议头正确
        if (!apiUrl.startsWith("http")) {
            apiUrl = "https://" + apiUrl;
        }

        try {
            JsonNode root = httpGetJson(apiUrl);
            if (root == null || root.path("code").asInt() != 200) {
                log.warn("Twitter API request failed or invalid response");
                return;
            }

            JsonNode tweet = root.path("tweet");
            if (tweet.isMissingNode()) return;

            String text = tweet.path("text").asText();
            long likes = tweet.path("likes").asLong();
            long retweets = tweet.path("retweets").asLong();
            String createdTimestamp = tweet.path("created_timestamp").asText(); // Unix timestamp
            String dateStr = tweet.path("created_at").asText(); // e.g. "Fri Feb 07 12:00:00 +0000 2025"

            JsonNode author = tweet.path("author");
            String name = author.path("name").asText();
            String screenName = author.path("screen_name").asText();
            String avatarUrl = author.path("avatar_url").asText();

            List<String> mediaUrls = new ArrayList<>();
            JsonNode media = tweet.path("media");
            if (media.has("photos")) {
                for (JsonNode photo : media.path("photos")) {
                    mediaUrls.add(photo.path("url").asText());
                }
            }
            // 如果有视频，可能需要处理视频封面或直接下载视频（暂只处理图片）
            if (media.has("videos")) {
                for (JsonNode video : media.path("videos")) {
                    mediaUrls.add(video.path("thumbnail_url").asText());
                }
            }

            // 生成卡片
            if (Boolean.TRUE.equals(config.getSendImage())) {
                File card = CardGenerator.generateTwitterCard(
                        name, screenName, avatarUrl, text, mediaUrls, dateStr,
                        likes, retweets, "Twitter/X", config.getTmpPath()
                );
                if (card != null && card.exists()) {
                    bot.sendGroupMsg(event.getGroupId(), MsgUtils.builder().img(card.toPath().toUri().toString()).build(), false);
                    return;
                }
            }

            // 文本兜底
            StringBuilder sb = new StringBuilder();
            sb.append(name).append(" (@").append(screenName).append(")\n");
            sb.append(text).append("\n");
            sb.append("❤ ").append(likes).append("  \uD83D\uDD01 ").append(retweets).append("\n");
            sb.append("链接: ").append(match);
            
            MsgUtils msg = MsgUtils.builder().text(sb.toString());
            if (config.getAnalysisDisplayImage()) {
                for (String url : mediaUrls) {
                    msg.img(url);
                }
            }
            bot.sendGroupMsg(event.getGroupId(), msg.build(), false);

        } catch (Exception e) {
            log.error("Twitter resolve error", e);
        }
    }

    private JsonNode httpGetJson(String url) throws IOException {
        Request request = new Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (compatible; WineFoxBot/1.0)")
                .build();
        try (Response resp = httpClient.newCall(request).execute()) {
            if (!resp.isSuccessful()) return null;
            return mapper.readTree(resp.body().byteStream());
        }
    }
}
