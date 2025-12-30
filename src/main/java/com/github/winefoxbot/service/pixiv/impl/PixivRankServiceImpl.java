package com.github.winefoxbot.service.pixiv.impl;

import cn.hutool.core.util.URLUtil;
import com.github.winefoxbot.config.PixivConfig;
import com.github.winefoxbot.model.dto.pixiv.PixivDetail;
import com.github.winefoxbot.model.enums.PixivRankPushMode;
import com.github.winefoxbot.service.pixiv.PixivRankService;
import com.github.winefoxbot.service.pixiv.PixivService;
import com.github.winefoxbot.utils.FileUtil;
import com.mikuac.shiro.common.utils.MsgUtils;
import com.mikuac.shiro.common.utils.ShiroUtils;
import com.mikuac.shiro.core.Bot;
import com.mikuac.shiro.core.BotContainer;
import com.mikuac.shiro.dto.action.common.ActionData;
import com.mikuac.shiro.dto.action.common.MsgId;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.apache.commons.io.FileUtils;
import org.apache.tomcat.util.buf.StringUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import javax.net.ssl.SSLHandshakeException;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author FlanChan (badapple495@outlook.com)
 * @since 2025-12-10-21:37
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PixivRankServiceImpl implements PixivRankService {
    private final OkHttpClient httpClient;
    private final PixivConfig pixivConfig;
    private final PixivService pixivService;
    private final BotContainer botContainer;
    private final String baseUrl = "https://www.pixiv.net/ranking.php";


    @Override
    public void fetchAndPushRank(Long groupId, PixivRankPushMode mode, Content content) {
        Optional<Bot> botOptional = botContainer.robots.values().stream().findFirst();
        if (botOptional.isEmpty()) {
            log.warn("没有可用的 Bot 实例来发送 Pixiv 排行榜");
            return;
        }
        Bot bot = botOptional.get();
        List<List<File>> filesList = null;
        try {
            List<String> msgList = new ArrayList<>();
            List<String> rankIds = this.getRank(mode, content, false);
            filesList = new ArrayList<>();
            for (String rankId : rankIds) {
                List<File> files = pixivService.fetchImages(rankId).join();
                if (files.isEmpty()) {
                    continue;
                }
                PixivDetail pixivDetail = pixivService.getPixivArtworkDetail(rankId);
                MsgUtils builder = MsgUtils.builder();
                builder.text(String.format("""
                                作品标题：%s (%s)
                                作者：%s (%s)
                                描述信息：%s
                                作品链接：https://www.pixiv.net/artworks/%s
                                标签：%s
                                """, pixivDetail.getTitle(), pixivDetail.getPid(),
                        pixivDetail.getUserName(), pixivDetail.getUid(),
                        pixivDetail.getDescription(),
                        pixivDetail.getPid(),
                        StringUtils.join(pixivDetail.getTags(), ',')));
                for (File file : files) {
                    String filePath = FileUtil.getFileUrlPrefix() + file.getAbsolutePath();
                    builder.img(filePath);
                }
                filesList.add(files);
                String msg = builder.build();
                msgList.add(msg);
            }
            if (msgList.isEmpty()) {
                bot.sendGroupMsg(groupId, "未能获取到排行榜数据", false);
                return;
            }
            List<Map<String, Object>> forwardMsg = ShiroUtils.generateForwardMsg(bot, msgList);
            String description = switch (mode) {
                case DALLY -> "每日";
                case WEEKLY -> "每周";
                case MONTHLY -> "每月";
            };
            bot.sendGroupMsg(groupId, "那么这是最新的 Pixiv %s排行榜~".formatted(description), false);
            ActionData<MsgId> resp = bot.sendGroupForwardMsg(groupId, forwardMsg);
            if (resp.getRetCode() == 0) {
                log.info("Pixiv {} 排行榜推送成功，群号: {}", description, groupId);
            } else {
                log.error("Pixiv {} 排行榜推送失败，错误码: {}，群号: {}", description, resp.getRetCode(), groupId);
                throw new RuntimeException("Pixiv 排行榜推送失败，错误码: " + resp.getRetCode());
            }
        } catch (SSLHandshakeException e) {
            log.error("Pixiv SSL 握手失败，可能是 Pixiv 证书发生变更导致，请检查！", e);
            bot.sendGroupMsg(groupId, "因为网络问题，图片获取失败，请重试", false);
        } catch (Exception e) {
            log.error("处理 Pixiv 图片失败", e);
            bot.sendGroupMsg(groupId, "处理 Pixiv 图片失败：" + e.getMessage(), false);
        } finally {
            // 删除文件
            clearFiles(filesList);
        }
    }

    @Async
    public void clearFiles (List<List<File>> filesList) {
        if (!filesList.isEmpty()) {
            for (List<File> files : filesList) {
                File parentFile = files.getFirst().getParentFile();
                if (parentFile.exists()) {
                    try {
                        FileUtils.deleteDirectory(parentFile);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }
            }
        }
    }

    /**
     * 获取排行榜 ID 列表（day / weekly / monthly）
     */
    @Override
    public List<String> getRank(PixivRankPushMode mode, PixivRankService.Content content, boolean enabledR18) throws IOException {
        String queryParams = URLUtil.buildQuery(Map.of(
                "mode", mode.getValue() + (enabledR18 ? "_r18" : ""),
                "content", content.getValue()
        ), StandardCharsets.UTF_8);
        String url = baseUrl + "?" + queryParams;
        Request request = new Request.Builder()
                .url(url)
                .headers(pixivConfig.getHeaders())
                .build();
        try (Response resp = httpClient.newCall(request).execute()) {
            if (!resp.isSuccessful()) {
                throw new IOException("Pixiv 请求失败：" + resp.code());
            }
            String html = resp.body().string();
            return parseArtworkIds(html);
        }
    }

    /**
     * 从 HTML 中解析出所有 artworks ID
     */
    private List<String> parseArtworkIds(String html) {
        Document doc = Jsoup.parse(html);
        Elements links = doc.select("a[href*=/artworks/]");

        Set<String> ids = new LinkedHashSet<>();

        for (var a : links) {
            String href = a.attr("href");
            Matcher m = Pattern.compile("/artworks/(\\d+)").matcher(href);
            if (m.find()) {
                ids.add(m.group(1));
            }
        }
        return new ArrayList<>(ids);
    }
}