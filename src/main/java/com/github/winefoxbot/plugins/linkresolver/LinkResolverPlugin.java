package com.github.winefoxbot.plugins.linkresolver;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.github.winefoxbot.core.annotation.plugin.Plugin;
import com.github.winefoxbot.core.util.MessageConverter;
import com.github.winefoxbot.plugins.linkresolver.config.LinkResolverConfig;
import com.github.winefoxbot.plugins.linkresolver.config.LinkResolverPluginConfig;
import com.github.winefoxbot.plugins.linkresolver.service.LinkResolverService;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.util.concurrent.Striped;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mikuac.shiro.annotation.GroupMessageHandler;
import com.mikuac.shiro.annotation.MessageHandlerFilter;
import com.mikuac.shiro.common.utils.ShiroUtils;
import com.mikuac.shiro.core.Bot;
import com.mikuac.shiro.dto.event.message.GroupMessageEvent;
import com.mikuac.shiro.enums.MsgTypeEnum;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.sisu.PostConstruct;
import org.springframework.scheduling.annotation.Async;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.github.winefoxbot.core.model.enums.common.PluginType.PASSIVE;

/**
 * 综合链接解析插件 (被动插件)
 * 支持 B站、Youtube、抖音、推特、小程序等链接解析
 *
 * @author FlanChan (badapple495@outlook.com)
 * @since 2026-02-06-19:49
 */
@Plugin(
        name = "综合链接解析",
        description = "提供对Bilibili、Youtube、抖音、Twitter等链接的解析功能。",
        type = PASSIVE,
        builtIn = true,
        order = 100,
        config = LinkResolverPluginConfig.class
)
@Slf4j
public class LinkResolverPlugin {

    private final List<LinkResolverService> resolvers;
    private final Cache<String, Boolean> historyCache;

    private final Gson gson = new Gson();

    public LinkResolverPlugin(List<LinkResolverService> resolvers, LinkResolverConfig linkResolverConfig) {
        this.resolvers = resolvers;
        this.historyCache = CacheBuilder.newBuilder()
                .expireAfterWrite(linkResolverConfig.getReanalysisTimeSeconds())
                .maximumSize(1000)
                .build();
    }


    private final Striped<Lock> keyLocks = Striped.lock(64);

    // 聚合所有解析器的正则，用于 MessageHandlerFilter
    // 添加 (?s) 启用 DOTALL 模式，使 . 匹配换行符，解决多行消息无法触发的问题
    private static final String FILTER_REGEX = "(?is).*(bilibili|b23\\.tv|BV[a-zA-Z0-9]{10}|av\\d+|cv\\d+|哔哩哔哩|youtube|youtu\\.be|douyin|iesdouyin|QQ小程序|twitter\\.com|x\\.com|m\\.q\\.qq\\.com).*";

    @Async
    @GroupMessageHandler
    @MessageHandlerFilter(types = {MsgTypeEnum.text, MsgTypeEnum.json}, cmd = FILTER_REGEX)
    public void resolveLink(Bot bot, GroupMessageEvent event) {

        try {
            String msgText = event.getMessage();
            String urlToParse = extractUrlFromMessage(msgText);

            if (urlToParse == null) {
                return;
            }

            // 遍历所有解析器，找到匹配的解析器
            for (LinkResolverService resolver : resolvers) {
                Matcher matcher = resolver.getRegex().matcher(urlToParse);
                if (matcher.find()) {
                    // 使用规范ID进行防重复处理
                    String canonicalId = resolver.getCanonicalId(urlToParse);
                    if (canonicalId == null || canonicalId.trim().isEmpty()) {
                        log.warn("无法为 URL提取规范 ID: {}", urlToParse);
                        // 无法获取规范ID时，回退到使用原始URL作为key，避免跳过处理
                        canonicalId = urlToParse;
                    }

                    String cacheKey = event.getGroupId() + '-' + canonicalId;
                    if (Boolean.TRUE.equals(historyCache.getIfPresent(cacheKey))) {
                        log.debug("URL 命中防重复缓存，跳过: {}", cacheKey);
                        return;
                    }

                    Lock lock = keyLocks.get(event.getGroupId() + '-' + cacheKey);
                    if (lock.tryLock()) {
                        try {
                            if (Boolean.TRUE.equals(historyCache.getIfPresent(cacheKey))) {
                                return;
                            }
                            resolver.resolve(bot, event, urlToParse);
                            historyCache.put(cacheKey, true);
                        } finally {
                            lock.unlock();
                        }
                    } else {
                        log.debug("URL 正在被其他线程处理，跳过并发请求: {}", cacheKey);
                    }
                    // 找到一个匹配的解析器后就停止，避免重复解析
                    return;
                }
            }

        } catch (Exception ex) {
            log.error("链接解析出错", ex);
        }
    }

    /**
     * 从消息中提取 URL
     * 兼容处理：
     * 1. 标准文本中的正则匹配
     * 2. 标准 JSON 类型的消息段 (OneBot v11)
     * 3. 嵌套在 Text 类型中的 CQ:json (旧版小程序分享/部分实现)
     * 4. 纯 JSON 字符串 (MsgTypeEnum.json)
     */
    private String extractUrlFromMessage(String msgText) {
        if (msgText == null || msgText.trim().isEmpty()) {
            return null;
        }

        // 0. 尝试直接解析纯 JSON 字符串 (针对 MsgTypeEnum.json)
        String trimmedMsg = msgText.trim();
        if (trimmedMsg.startsWith("{") && trimmedMsg.endsWith("}")) {
            String url = extractQqDocUrl(trimmedMsg);
            if (url != null) return url;
        }

        JSONArray segments;
        try {
            if (JSONUtil.isTypeJSONArray(msgText)) {
                segments = JSONUtil.parseArray(msgText);
            } else {
                segments = MessageConverter.parseCQToJSONArray(msgText);
            }
        } catch (Exception e) {
            log.debug("消息格式解析失败，尝试直接正则匹配: {}", e.getMessage());
            segments = new JSONArray();
        }

        // 2. 遍历消息段查找 URL
        for (Object item : segments) {
            if (!(item instanceof JSONObject segment)) continue;

            String type = segment.getStr("type");
            JSONObject data = segment.getJSONObject("data");
            if (data == null) continue;

            // --- 情况 A: 这是一个 JSON 卡片消息 ---
            if ("json".equals(type)) {
                String innerJsonStr = data.getStr("data");
                String url = extractQqDocUrl(innerJsonStr);
                if (url != null) return url;
            }

            // --- 情况 B: 这是一个 Text 消息 (可能包含嵌套 CQ:json 或普通链接) ---
            if ("text".equals(type)) {
                String text = data.getStr("text");
                if (text == null || text.isEmpty()) continue;

                // B1. 检查是否是“伪装”成 text 的 CQ:json
                if (text.contains("[CQ:json")) {
                    // 优先尝试正则提取，因为 MessageConverter 可能解析失败
                    String url = extractUrlFromMalformedCQJson(text);
                    if (url != null) return url;

                    // 如果正则失败，再尝试解析
                    try {
                        JSONArray nestedSegments = MessageConverter.parseCQToJSONArray(text);
                        for (Object nestedItem : nestedSegments) {
                            if (!(nestedItem instanceof JSONObject nestedSeg)) continue;
                            if ("json".equals(nestedSeg.getStr("type"))) {
                                JSONObject nestedData = nestedSeg.getJSONObject("data");
                                if (nestedData != null) {
                                    String innerJsonStr = nestedData.getStr("data");
                                    String innerUrl = extractQqDocUrl(innerJsonStr);
                                    if (innerUrl != null) return innerUrl;
                                }
                            }
                        }
                    } catch (Exception ignored) {}

                    // 最后的兜底：直接在文本中查找经过转义的链接
                    // 1. m.q.qq.com
                    Pattern p1 = Pattern.compile("m\\.q\\.qq\\.com\\\\/a\\\\/s\\\\/[a-zA-Z0-9]+");
                    Matcher m1 = p1.matcher(text);
                    if (m1.find()) {
                        String captured = m1.group();
                        log.info("通过兜底正则提取到转义 URL: {}", captured);
                        return captured.replace("\\/", "/");
                    }
                    
                    // 2. b23.tv
                    Pattern p2 = Pattern.compile("b23\\.tv\\\\/[a-zA-Z0-9]+");
                    Matcher m2 = p2.matcher(text);
                    if (m2.find()) {
                         String captured = m2.group();
                         log.info("通过兜底正则提取到转义 URL: {}", captured);
                         return captured.replace("\\/", "/");
                    }
                }

                // B2. 如果没找到小程序链接，则尝试匹配所有解析器的正则
                for (LinkResolverService resolver : resolvers) {
                    Matcher matcher = resolver.getRegex().matcher(text);
                    if (matcher.find()) {
                        String captured = matcher.group();
                        if (captured != null && !captured.isEmpty()) {
                            return captured;
                        }
                    }
                }
            }
        }

        // 3. 兜底正则匹配
        if (segments.isEmpty()) {
            // 尝试作为 CQ:json 处理
            if (msgText.contains("[CQ:json")) {
                String url = extractUrlFromMalformedCQJson(msgText);
                if (url != null) return url;
                
                // 兜底正则
                Pattern p = Pattern.compile("m\\.q\\.qq\\.com\\\\/a\\\\/s\\\\/[a-zA-Z0-9]+");
                Matcher m = p.matcher(msgText);
                if (m.find()) {
                    return m.group().replace("\\/", "/");
                }
            }

            for (LinkResolverService resolver : resolvers) {
                Matcher matcher = resolver.getRegex().matcher(msgText);
                if (matcher.find()) {
                    return matcher.group();
                }
            }
        }

        return null;
    }

    /**
     * 尝试解析格式不标准的 CQ:json (例如包含换行符或未转义字符)
     */
    private String extractUrlFromMalformedCQJson(String text) {
        // 策略1: 正则匹配 data={...}
        Pattern pattern = Pattern.compile("data=(\\{.*\\})", Pattern.DOTALL);
        Matcher matcher = pattern.matcher(text);
        if (matcher.find()) {
            String jsonStr = matcher.group(1);
            // 截取到最后一个 }
            int lastBrace = jsonStr.lastIndexOf("}");
            if (lastBrace != -1) {
                String candidate = jsonStr.substring(0, lastBrace + 1);
                String url = extractQqDocUrl(candidate);
                if (url != null) return url;
            }
        }
        
        // 策略2: 简单粗暴提取第一个 { 到最后一个 }
        int start = text.indexOf("{");
        int end = text.lastIndexOf("}");
        if (start != -1 && end > start) {
             String jsonStr = text.substring(start, end + 1);
             String url = extractQqDocUrl(jsonStr);
             if (url != null) return url;
        }
        return null;
    }

    /**
     * 辅助方法：解析 QQ 小程序 JSON 字符串结构，提取 qqdocurl
     * 路径：data(string) -> meta -> detail_1 -> qqdocurl
     */
    private String extractQqDocUrl(String jsonStr) {
        if (jsonStr == null || jsonStr.isEmpty()) return null;

        // 使用 ShiroUtils 进行解码
        jsonStr = ShiroUtils.unescape(jsonStr);
        // 二次清洗，防止双重转义或 ShiroUtils 未处理的字符
        jsonStr = jsonStr.replace("&#44;", ",")
                         .replace("&#91;", "[")
                         .replace("&#93;", "]");

        try {
            JsonObject innerData = gson.fromJson(jsonStr, JsonObject.class);
            JsonElement metaEl = innerData.get("meta");
            if (metaEl != null && metaEl.isJsonObject()) {
                JsonElement detailEl = metaEl.getAsJsonObject().get("detail_1");
                if (detailEl != null && detailEl.isJsonObject()) {
                    JsonObject detailObj = detailEl.getAsJsonObject();
                    
                    // 优先查找 qqdocurl
                    JsonElement urlEl = detailObj.get("qqdocurl");
                    if (urlEl != null && !urlEl.isJsonNull()) {
                        String url = urlEl.getAsString();
                        if (url != null && !url.isEmpty()) {
                            log.info("从小程序结构中提取到 qqdocurl: {}", url);
                            return url;
                        }
                    }
                    
                    // 其次查找 url (部分小程序可能使用 url 字段)
                    JsonElement fallbackUrlEl = detailObj.get("url");
                    if (fallbackUrlEl != null && !fallbackUrlEl.isJsonNull()) {
                        String url = fallbackUrlEl.getAsString();
                        if (url != null && !url.isEmpty()) {
                            log.info("从小程序结构中提取到 url: {}", url);
                            return url;
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.debug("JSON 解析失败: {}, 尝试正则提取", e.getMessage());
            // 正则提取 fallback
            // 优先提取 qqdocurl
            Pattern p = Pattern.compile("\"qqdocurl\"\\s*:\\s*\"([^\"]+)\"");
            Matcher m = p.matcher(jsonStr);
            if (m.find()) {
                String url = m.group(1);
                log.info("通过正则提取到 qqdocurl: {}", url);
                return url.replace("\\/", "/");
            }
            
            // 其次提取 url
            Pattern p2 = Pattern.compile("\"url\"\\s*:\\s*\"([^\"]+)\"");
            Matcher m2 = p2.matcher(jsonStr);
            if (m2.find()) {
                String url = m2.group(1);
                log.info("通过正则提取到 url: {}", url);
                return url.replace("\\/", "/");
            }
        }
        return null;
    }
}
