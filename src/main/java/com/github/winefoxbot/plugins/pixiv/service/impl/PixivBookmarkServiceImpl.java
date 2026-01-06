package com.github.winefoxbot.plugins.pixiv.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.winefoxbot.core.constants.CacheConstants;
import com.github.winefoxbot.core.constants.ConfigConstants;
import com.github.winefoxbot.core.exception.common.BusinessException;
import com.github.winefoxbot.core.manager.ConfigManager;
import com.github.winefoxbot.plugins.pixiv.config.PixivProperties;
import com.github.winefoxbot.plugins.pixiv.mapper.PixivBookmarkMapper;
import com.github.winefoxbot.plugins.pixiv.model.dto.bookmark.PixivApiBody;
import com.github.winefoxbot.plugins.pixiv.model.dto.bookmark.PixivArtwork;
import com.github.winefoxbot.plugins.pixiv.model.dto.bookmark.PixivUnmarkApiRequest;
import com.github.winefoxbot.plugins.pixiv.model.dto.bookmark.PixivUnmarkApiResponse;
import com.github.winefoxbot.plugins.pixiv.model.entity.PixivBookmark;
import com.github.winefoxbot.plugins.pixiv.model.enums.PixivRatingLevel;
import com.github.winefoxbot.plugins.pixiv.service.PixivBookmarkService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.http.HttpHeaders;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * @author FlanChan
 * @description 针对表【pixiv_bookmark(存储特定用户 P站收藏作品的信息)】的数据库操作Service实现
 * @createDate 2026-01-04 16:05:55
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PixivBookmarkServiceImpl extends ServiceImpl<PixivBookmarkMapper, PixivBookmark>
        implements PixivBookmarkService {

    private final OkHttpClient okHttpClient;
    private final ObjectMapper objectMapper;
    private final PixivProperties pixivProperties;
    private final RedisTemplate<String,String> redisTemplate;
    private final ConfigManager configManager;
    private PixivBookmarkService self;

    private final AtomicBoolean isSyncInProgress = new AtomicBoolean(false);
    private final AtomicBoolean isLightSyncInProgress = new AtomicBoolean(false);
    private final Random random = new Random();
    private static final double INITIAL_WEIGHT = 100.0;
    private static final double WEIGHT_RESET_THRESHOLD = 20.0; // 平均权重低于20时重置权重保证随机性

    @Autowired
    @Lazy
    public void setSelf(PixivBookmarkService self) {
        this.self = self;
    }

    @PostConstruct
    public void initOrCheckBookmarkWeights() {
        // 同时检查所有 ZSET
        boolean allExist = Stream.of(
                CacheConstants.ZSET_BOOKMARK_WEIGHTS_KEY_MIX,
                CacheConstants.ZSET_BOOKMARK_WEIGHTS_KEY_SFW,
                CacheConstants.ZSET_BOOKMARK_WEIGHTS_KEY_R18
        ).allMatch(key -> Boolean.TRUE.equals(redisTemplate.hasKey(key)));

        if (allExist) {
            log.info("Redis bookmark weights ZSETs (MIX, SFW, R18) 已存在，跳过初始化。");
            return;
        }

        log.info("一个或多个 Redis bookmark weights ZSETs 不存在，开始从数据库初始化...");

        // 从数据库获取包含分级信息的完整对象
        List<PixivBookmark> allBookmarks = this.list(new QueryWrapper<PixivBookmark>().select("id", "x_restrict"));

        if (CollectionUtils.isEmpty(allBookmarks)) {
            log.warn("数据库中没有 Bookmark 数据，ZSET 初始化中止。");
            return;
        }

        ZSetOperations<String, String> zSetOps = redisTemplate.opsForZSet();

        // 为不同模式创建不同的 member 集合
        Set<ZSetOperations.TypedTuple<String>> mixMembers = new HashSet<>();
        Set<ZSetOperations.TypedTuple<String>> sfwMembers = new HashSet<>();
        Set<ZSetOperations.TypedTuple<String>> r18Members = new HashSet<>();

        for (PixivBookmark bookmark : allBookmarks) {
            String id = bookmark.getId();
            ZSetOperations.TypedTuple<String> tuple = ZSetOperations.TypedTuple.of(id, 100.0);

            mixMembers.add(tuple);

            // 【核心改动】使用辅助方法判断并分类
            if (isR18(bookmark)) {
                r18Members.add(tuple);
            } else {
                sfwMembers.add(tuple);
            }
        }

        // 批量添加并设置过期时间
        if (!mixMembers.isEmpty()) {
            zSetOps.add(CacheConstants.ZSET_BOOKMARK_WEIGHTS_KEY_MIX, mixMembers);
            redisTemplate.expire(CacheConstants.ZSET_BOOKMARK_WEIGHTS_KEY_MIX, 30, TimeUnit.DAYS);
        }
        if (!sfwMembers.isEmpty()) {
            zSetOps.add(CacheConstants.ZSET_BOOKMARK_WEIGHTS_KEY_SFW, sfwMembers);
            redisTemplate.expire(CacheConstants.ZSET_BOOKMARK_WEIGHTS_KEY_SFW, 30, TimeUnit.DAYS);
        }
        if (!r18Members.isEmpty()) {
            zSetOps.add(CacheConstants.ZSET_BOOKMARK_WEIGHTS_KEY_R18, r18Members);
            redisTemplate.expire(CacheConstants.ZSET_BOOKMARK_WEIGHTS_KEY_R18, 30, TimeUnit.DAYS);
        }

        log.info("成功初始化 Bookmark weights ZSETs。 MIX: {}, SFW: {}, R18: {}",
                mixMembers.size(), sfwMembers.size(), r18Members.size());
    }

    /**
     * 根据 xRestrict 属性判断作品是否为 R18 或 R18-G。
     * @param bookmark PixivBookmark 对象
     * @return 如果是 R18 或 R18-G 则返回 true，否则返回 false。
     */
    private boolean isR18(PixivBookmark bookmark) {
        if (bookmark == null || bookmark.getXRestrict() == null) {
            return false; // 默认视为 SFW
        }
        // 根据枚举类型判断。假设枚举名为 RESTRICTED 和 R18G
        return bookmark.getXRestrict() == PixivRatingLevel.R18 ||
                bookmark.getXRestrict() == PixivRatingLevel.R18G;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void syncBookmarks() {
        if (isSyncInProgress.get()) {
            log.warn("已有同步任务在进行中，跳过此次请求。");
            throw new BusinessException("已有同步任务在进行中，无法执行新的同步请求。");
        }
        isSyncInProgress.set(true);

        PixivProperties.Tracker trackerProps = pixivProperties.getBookmark().getTracker();
        String userId = trackerProps.getTargetUserId();
        log.info("开始同步用户 [{}] 的P站收藏夹...", userId);

        try {
            LambdaQueryWrapper<PixivBookmark> queryWrapper = new LambdaQueryWrapper<PixivBookmark>()
                    .eq(PixivBookmark::getTrackedUserId, userId)
                    .select(PixivBookmark::getId); // 只查询 id 字段，提高效率

            Set<String> dbBookmarkIds = this.list(queryWrapper)
                    .stream()
                    .map(PixivBookmark::getId)
                    .collect(Collectors.toSet());
            log.info("用户 [{}] 数据库中已存在 {} 条收藏记录。", userId, dbBookmarkIds.size());

            // 2. 从P站API获取所有收藏
            Map<String, PixivArtwork> remoteBookmarks = fetchAllBookmarks(userId);
            if (remoteBookmarks.isEmpty()) {
                log.warn("从P站API获取用户 [{}] 的收藏夹失败或为空，同步中止。", userId);
                return;
            }
            log.info("从P站API成功获取 {} 条收藏记录。", remoteBookmarks.size());

            // 3. 比较差异并更新数据库
            updateDatabase(userId, dbBookmarkIds, remoteBookmarks);

            log.info("用户 [{}] 的收藏夹同步完成。", userId);

        } catch (Exception e) {
            log.error("同步用户 [{}] 收藏夹时发生严重错误。", userId, e);
            // 可以在此抛出运行时异常，以便事务回滚
             throw new RuntimeException("同步失败", e);
        } finally {
            isSyncInProgress.set(false);
        }
    }

    /**
     * 定时任务入口。
     * 解决了事务自调用警告的问题。
     */
    @Scheduled(cron = "${pixiv.bookmark.tracker.full-cron}")
    @Override
    public void scheduleSyncBookmarks() {
        if (!pixivProperties.getBookmark().getTracker().getEnabled()) {
            log.info("P站收藏夹同步任务已禁用，跳过执行。");
            return;
        }
        log.info("定时任务触发：开始同步P站收藏夹。");
        try {
            self.syncBookmarks();
        } catch (BusinessException ex) {
            log.warn("P站收藏夹同步被跳过: {}", ex.getMessage());
        } catch (Exception e) {
            log.error("定时同步P站收藏夹时发生错误。", e);
        }
    }


    @Transactional(rollbackFor = Exception.class)
    @Override
    public void syncLatestBookmarks() {
        if (isSyncInProgress.get() || isLightSyncInProgress.get()) {
            log.warn("已有同步任务在进行中，跳过此次轻量同步。");
            return;
        }
        isLightSyncInProgress.set(true);

        PixivProperties.Tracker trackerProps = pixivProperties.getBookmark().getTracker();
        String userId = trackerProps.getTargetUserId();
        log.info("开始轻量同步用户 [{}] 的P站收藏夹第一页...", userId);

        try {
            int limit = pixivProperties.getApi().getLimitPerPage();
            Optional<PixivApiBody> pageResult = fetchSinglePage(userId, 0, limit);

            if (pageResult.isEmpty() || CollectionUtils.isEmpty(pageResult.get().getWorks())) {
                log.warn("轻量同步：从P站API获取用户 [{}] 的收藏夹第一页失败或为空，同步中止。", userId);
                return;
            }

            List<PixivBookmark> latestArtworks = pageResult.get().getWorks().stream()
                    .map(dto -> convertToEntity(dto, userId))
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());

            if (latestArtworks.isEmpty()) {
                log.info("轻量同步：第一页没有有效的作品，无需操作。");
                return;
            }

            // 【新增逻辑：在更新数据库前，找出数据库中已存在的ID】
            Set<String> latestArtworkIds = latestArtworks.stream()
                    .map(PixivBookmark::getId)
                    .collect(Collectors.toSet());

            // 查询这批ID中有哪些是数据库里已经存在的
            List<PixivBookmark> existingBookmarks = this.listByIds(latestArtworkIds);
            Set<String> existingIds = existingBookmarks.stream()
                    .map(PixivBookmark::getId)
                    .collect(Collectors.toSet());


            // 批量保存或更新数据库
            log.info("轻量同步：准备新增或更新 {} 条最新收藏记录...", latestArtworks.size());
            this.saveOrUpdateBatch(latestArtworks);

            // 【新增逻辑：处理Redis ZSET缓存】
            // 1. 筛选出本次操作中真正新增的作品
            List<PixivBookmark> newBookmarks = latestArtworks.stream()
                    .filter(bookmark -> !existingIds.contains(bookmark.getId()))
                    .toList();

            if (!newBookmarks.isEmpty()) {
                log.info("轻量同步：检测到 {} 条新增作品，开始更新 Redis ZSET 缓存...", newBookmarks.size());
                try {
                    ZSetOperations<String, String> zSetOps = redisTemplate.opsForZSet();

                    Set<ZSetOperations.TypedTuple<String>> newMixMembers = new HashSet<>();
                    Set<ZSetOperations.TypedTuple<String>> newSfwMembers = new HashSet<>();
                    Set<ZSetOperations.TypedTuple<String>> newR18Members = new HashSet<>();

                    for (PixivBookmark bookmark : newBookmarks) {
                        ZSetOperations.TypedTuple<String> tuple = ZSetOperations.TypedTuple.of(bookmark.getId(), INITIAL_WEIGHT);
                        newMixMembers.add(tuple);
                        if (isR18(bookmark)) {
                            newR18Members.add(tuple);
                        } else {
                            newSfwMembers.add(tuple);
                        }
                    }

                    if (!newMixMembers.isEmpty()) zSetOps.add(CacheConstants.ZSET_BOOKMARK_WEIGHTS_KEY_MIX, newMixMembers);
                    if (!newSfwMembers.isEmpty()) zSetOps.add(CacheConstants.ZSET_BOOKMARK_WEIGHTS_KEY_SFW, newSfwMembers);
                    if (!newR18Members.isEmpty()) zSetOps.add(CacheConstants.ZSET_BOOKMARK_WEIGHTS_KEY_R18, newR18Members);

                    log.info("轻量同步：成功向 Redis ZSETs 新增了 {} 个 ID。", newBookmarks.size());
                } catch (Exception e) {
                    // 这里只记录错误，不影响主流程，因为数据库已经成功更新
                    log.error("轻量同步期间更新 Redis ZSET 缓存失败！缓存将与数据库不一致，等待下一次全量同步修复。", e);
                }
            } else {
                log.info("轻量同步：没有新增作品，无需更新 Redis 缓存。");
            }

            log.info("用户 [{}] 的收藏夹第一页轻量同步完成。", userId);

        } catch (Exception e) {
            log.error("轻量同步用户 [{}] 收藏夹时发生错误。", userId, e);
            throw new RuntimeException("轻量同步失败", e);
        } finally {
            isLightSyncInProgress.set(false);
        }
    }




    @Scheduled(cron = "${pixiv.bookmark.tracker.light-cron}")
    @Override
    public void scheduleSyncLatestBookmarks() {
        if (!pixivProperties.getBookmark().getTracker().getEnabled()) {
            // 如果总开关是关的，那么所有同步都跳过
            return;
        }
        log.info("定时任务触发：开始轻量同步P站收藏夹第一页。");
        try {
            // 使用 self 调用以确保事务生效
            self.syncLatestBookmarks();
        } catch (Exception e) {
            // 这里只记录错误，不向上抛出，避免影响定时任务调度器
            log.error("定时轻量同步P站收藏夹时发生错误。", e);
        }
    }


    private Map<String, PixivArtwork> fetchAllBookmarks(String userId) throws IOException {
        Map<String, PixivArtwork> allArtworks = new LinkedHashMap<>();
        int offset = 0;
        int limit = pixivProperties.getApi().getLimitPerPage();
        boolean isFirstPage = true;
        int total = 0;

        while (true) {
            log.info("正在获取用户 [{}] 的收藏，offset={}, limit={}", userId, offset, limit);
            Optional<PixivApiBody> pageResult = fetchSinglePage(userId, offset, limit);

            if (pageResult.isEmpty() || CollectionUtils.isEmpty(pageResult.get().getWorks())) {
                log.warn("获取页面 offset={} 失败或无更多作品，停止抓取。", offset);
                break;
            }

            PixivApiBody body = pageResult.get();
            for (PixivArtwork artwork : body.getWorks()) {
                allArtworks.put(artwork.getId(), artwork);
            }

            if (isFirstPage) {
                total = body.getTotal();
                log.info("检测到总收藏数为: {}", total);
                isFirstPage = false;
            }

            offset += body.getWorks().size();
            if (offset >= total) {
                log.info("已获取所有收藏作品 ({} / {}).", offset, total);
                break;
            }

            try {
                Thread.sleep(1000 + new Random().nextInt(1500));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("收藏抓取任务被中断。");
                break;
            }
        }
        return allArtworks;
    }

    private Optional<PixivApiBody> fetchSinglePage(String userId, int offset, int limit) throws IOException {

        PixivProperties.ApiProperties apiProps = pixivProperties.getApi();
        PixivProperties.CookieProperties cookieProps = pixivProperties.getCookie();

        String url = apiProps.getBookmarkUrlTemplate()
                .replace("{userId}", userId)
                .replace("{offset}", String.valueOf(offset))
                .replace("{limit}", String.valueOf(limit))
                .replace("{tag}", "");

        String cookie = String.format("PHPSESSID=%s; p_ab_id=%s;", cookieProps.getPhpsessid(), cookieProps.getPAbId());

        Request request = new Request.Builder()
                .url(url)
                .get()
                .addHeader("Cookie", cookie)
                .addHeader(HttpHeaders.ACCEPT, "application/json")
                .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .addHeader("Referer", "https://www.pixiv.net/")
                .build();

        try (Response response = okHttpClient.newCall(request).execute()) {
            String jsonStr = response.body().string(); // 先把 body 读出来

            if (!response.isSuccessful()) {
                log.error("请求失败! URL: {}, 响应码: {}, 响应消息: {}", url, response.code(), response.message());
                // 打印响应体以帮助调试
                log.error("响应体内容: {}", jsonStr);
                return Optional.empty();
            }

            // **关键修改点：增加对响应内容的检查**
            if (jsonStr == null || jsonStr.trim().isEmpty() || !jsonStr.trim().startsWith("{")) {
                log.error("从P站API收到了非预期的响应内容，可能Cookie已失效或触发了反爬虫机制。");
                log.error("URL: {}", url);
                log.error("响应码: {}", response.code());
                // 只打印前 500 个字符，避免日志过长
                log.error("响应体开头: {}", jsonStr.substring(0, Math.min(jsonStr.length(), 500)));
                return Optional.empty(); // 直接返回空，不再尝试解析
            }

            try {
                // **原有的逻辑用 try-catch 包裹起来，增加一层保护**
                // 注意：JsonPath.read 的结果可能是 JSON 对象或数组，直接 toString 可能不是你想要的
                // 一个更安全的方式是直接让 Jackson 处理
                JsonNode rootNode = objectMapper.readTree(jsonStr);
                JsonNode bodyNode = rootNode.path("body"); // 使用 .path() 更安全，找不到不会抛异常

                if (bodyNode.isMissingNode()) {
                    log.error("响应JSON中未找到 'body' 字段。响应内容: {}", jsonStr);
                    return Optional.empty();
                }

                return Optional.ofNullable(objectMapper.treeToValue(bodyNode, PixivApiBody.class));

            } catch (Exception e) {
                log.error("解析P站API响应JSON时发生错误", e);
                log.error("原始JSON字符串: {}", jsonStr); // 打印原始字符串以供分析
                return Optional.empty();
            }
        }
    }

    private void updateDatabase(String userId, Set<String> dbIds, Map<String, PixivArtwork> remoteData) {
        List<PixivBookmark> toAddOrUpdate = new ArrayList<>();
        // remoteIds 现在只包含有效的作品ID
        Set<String> remoteValidIds = remoteData.values().stream()
                .filter(artwork -> artwork.getAuthorId() != null && !artwork.getIsMasked()) // 过滤掉脏数据
                .map(artwork -> String.valueOf(artwork.getId()))
                .collect(Collectors.toSet());

        // 1. 遍历远程数据，找出需要新增或更新的
        for (PixivArtwork dto : remoteData.values()) {
            PixivBookmark entity = convertToEntity(dto, userId);
            if (entity != null) { // convertToEntity 返回 null 说明是脏数据，直接跳过
                toAddOrUpdate.add(entity);
            }
        }

        // 2. 批量保存或更新
        if (!toAddOrUpdate.isEmpty()) {
            log.info("准备新增或更新 {} 条收藏记录...", toAddOrUpdate.size());
            this.saveOrUpdateBatch(toAddOrUpdate);
        }

        // 3. 找出需要删除的：本地有，但远程有效收藏列表里没有的
        Set<String> toDeleteIds = new HashSet<>(dbIds);
        toDeleteIds.removeAll(remoteValidIds); // 求差集

        if (!toDeleteIds.isEmpty()) {
            log.info("检测到 {} 条收藏已在P站被删除或设为不可见，准备从本地数据库删除...", toDeleteIds.size());
            this.removeByIds(toDeleteIds);
        }
        log.info("数据库同步完成。新增/更新: {}, 删除: {}", toAddOrUpdate.size(), toDeleteIds.size());

        log.info("开始增量同步 Redis 权重缓存 (ZSET)...");
        try {
            ZSetOperations<String, String> zSetOps = redisTemplate.opsForZSet();

            // 1. 从所有 ZSET 中移除已删除的 ID
            if (!toDeleteIds.isEmpty()) {
                Object[] idsToRemove = toDeleteIds.toArray(new Object[0]);
                zSetOps.remove(CacheConstants.ZSET_BOOKMARK_WEIGHTS_KEY_MIX, idsToRemove);
                zSetOps.remove(CacheConstants.ZSET_BOOKMARK_WEIGHTS_KEY_SFW, idsToRemove);
                zSetOps.remove(CacheConstants.ZSET_BOOKMARK_WEIGHTS_KEY_R18, idsToRemove);
                log.info("从 Redis ZSETs 中移除了 {} 个过期 ID。", toDeleteIds.size());
            }

            // 2. 向 ZSET 中添加新增的 ID
            List<PixivBookmark> newBookmarks = toAddOrUpdate.stream()
                    .filter(bookmark -> !dbIds.contains(bookmark.getId()))
                    .toList();

            if (!newBookmarks.isEmpty()) {
                Set<ZSetOperations.TypedTuple<String>> newMixMembers = new HashSet<>();
                Set<ZSetOperations.TypedTuple<String>> newSfwMembers = new HashSet<>();
                Set<ZSetOperations.TypedTuple<String>> newR18Members = new HashSet<>();

                for (PixivBookmark bookmark : newBookmarks) {
                    ZSetOperations.TypedTuple<String> tuple = ZSetOperations.TypedTuple.of(bookmark.getId(), 100.0);
                    newMixMembers.add(tuple);

                    // 【核心改动】使用辅助方法判断并分类
                    if (isR18(bookmark)) {
                        newR18Members.add(tuple);
                    } else {
                        newSfwMembers.add(tuple);
                    }
                }

                if (!newMixMembers.isEmpty()) zSetOps.add(CacheConstants.ZSET_BOOKMARK_WEIGHTS_KEY_MIX, newMixMembers);
                if (!newSfwMembers.isEmpty()) zSetOps.add(CacheConstants.ZSET_BOOKMARK_WEIGHTS_KEY_SFW, newSfwMembers);
                if (!newR18Members.isEmpty()) zSetOps.add(CacheConstants.ZSET_BOOKMARK_WEIGHTS_KEY_R18, newR18Members);

                log.info("向 Redis ZSETs 中新增了 ID。 MIX: {}, SFW: {}, R18: {}",
                        newMixMembers.size(), newSfwMembers.size(), newR18Members.size());
            }

        } catch (Exception e) {
            log.error("增量更新 Redis ZSET 缓存失败！缓存可能与数据库不一致！", e);
        }
        log.info("Redis ZSET 缓存同步完成。");
    }

    private PixivBookmark convertToEntity(PixivArtwork dto, String trackedUserId) {
        // 关键判断：过滤掉已删除或不可见的作品
        // 使用最可靠的 userId 和 isMasked 字段
        if (dto.getAuthorId() == null || dto.getIsMasked()) {
            log.warn("检测到已删除或不可见的作品，将跳过。作品ID: {}", dto.getId());
            unmarkArtwork(dto.getBookmarkData());
            return null; // 返回 null，表示这是一个无效作品，不应处理
        }

        PixivBookmark entity = new PixivBookmark();
        entity.setId(dto.getId());
        entity.setTrackedUserId(trackedUserId);
        entity.setTitle(dto.getTitle());
        entity.setIllustType(dto.getIllustType());
        entity.setXRestrict(PixivRatingLevel.fromValue(dto.getXRestrict()));
        entity.setSlLevel(dto.getSl());
        entity.setImageUrl(dto.getImageUrl());
        entity.setAuthorId(dto.getAuthorId());
        entity.setAuthorName(dto.getAuthorName());
        entity.setWidth(dto.getWidth());
        entity.setAiType(dto.getAiType());
        entity.setDescription(dto.getDescription());
        entity.setHeight(dto.getHeight());
        entity.setPageCount(dto.getPageCount());
        entity.setPixivCreateDate(dto.getCreateDate().toLocalDateTime());
        entity.setPixivUpdateDate(dto.getUpdateDate().toLocalDateTime());
        entity.setTags(dto.getTags() != null ? dto.getTags() : Collections.emptyList());
        return entity;
    }

    @Async
    protected void unmarkArtwork(PixivArtwork.BookMarkData bookMarkData) {
        if (!pixivProperties.getBookmark().getAllowUnmarkExpiredArtworks()) {
            return;
        }
        log.info("尝试取消收藏过期作品ID: {}", bookMarkData.getId());
        PixivProperties.ApiProperties apiProps = pixivProperties.getApi();
        PixivProperties.CookieProperties cookieProps = pixivProperties.getCookie();
        String csrfToken = pixivProperties.getAuthorization().getXcsrfToken();
        String phpsessid = cookieProps.getPhpsessid();
        String pAbId = cookieProps.getPAbId();
        if (csrfToken == null || csrfToken.isEmpty() ||
                phpsessid == null || phpsessid.isEmpty() ||
                pAbId == null || pAbId.isEmpty()) {
            log.error("取消收藏失败，缺少必要的认证信息（X-CSRF-Token 或 Cookie）。");
            return;
        }
        String cookie = String.format("PHPSESSID=%s; p_ab_id=%s;", phpsessid, pAbId);
        // 构建请求
        PixivUnmarkApiRequest unmarkRequest = new PixivUnmarkApiRequest();
        unmarkRequest.setBookmarkId(bookMarkData.getId());

        RequestBody formBody = new FormBody.Builder()
                .add("bookmark_id", unmarkRequest.getBookmarkId())
                .build();

        Request request = new Request.Builder()
                .url(apiProps.getUnmaskUrlTemplate())
                .post(formBody)
                .addHeader("Cookie", cookie)
                .addHeader(HttpHeaders.ACCEPT, "application/json")
                .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .addHeader("Referer", "https://www.pixiv.net/")
                .addHeader("Origin", "https://www.pixiv.net") // 最好也加上 Origin
                .addHeader("x-csrf-token", csrfToken) // <-- 添加关键的请求头！
                .build();
        try (Response response = okHttpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                log.error("取消收藏请求失败! 作品ID: {}, 响应码: {}, 响应消息: {}", bookMarkData.getId(), response.code(), response.message());
            } else {
                ResponseBody body = response.body();
                if (body == null) {
                    log.error("取消收藏请求响应体为空! 作品ID: {}", bookMarkData.getId());
                    return;
                }
                String jsonStr = body.string();
                JsonNode rootNode = objectMapper.readTree(jsonStr);
                if (rootNode == null) {
                    log.error("取消收藏请求响应JSON解析失败! 作品ID: {}, 响应内容: {}", bookMarkData.getId(), jsonStr);
                    return;
                }
                Optional<PixivUnmarkApiResponse> pixivUnmarkApiResponse = Optional.ofNullable(objectMapper.treeToValue(rootNode, PixivUnmarkApiResponse.class));
                if (pixivUnmarkApiResponse.isEmpty() || Boolean.TRUE.equals(pixivUnmarkApiResponse.get().getError())) {
                    log.error("取消收藏请求返回错误! 作品ID: {}, 响应内容: {}", bookMarkData.getId(), jsonStr);
                    return;
                }
                log.info("成功取消收藏作品ID: {}", bookMarkData.getId());
            }
        } catch (IOException e) {
            log.error("取消收藏请求时发生IO异常! 作品ID: {}", bookMarkData.getId(), e);
        }

    }

    /**
     * 使用加权随机算法从 Redis ZSET 中获取一个 Bookmark ID。
     * @return 随机抽取的 Bookmark ID Optional
     */
    @Override
    public Optional<PixivBookmark> getRandomBookmark(Long userId, Long groupId) {
        String contentMode = configManager.getOrDefault(ConfigConstants.AdultContent.SETU_CONTENT_MODE, userId, groupId, ConfigConstants.AdultContent.MODE_SFW);
        String zsetKey = null;
        switch (contentMode) {
            case ConfigConstants.AdultContent.MODE_MIX -> {
                zsetKey = CacheConstants.ZSET_BOOKMARK_WEIGHTS_KEY_MIX;
                log.debug("Content mode is MIX, using ZSET: {}", zsetKey);
            }
            case ConfigConstants.AdultContent.MODE_R18 -> {
                zsetKey = CacheConstants.ZSET_BOOKMARK_WEIGHTS_KEY_R18;
                log.debug("Content mode is R18, using ZSET: {}", zsetKey);
            }
            case ConfigConstants.AdultContent.MODE_SFW -> {
                zsetKey = CacheConstants.ZSET_BOOKMARK_WEIGHTS_KEY_SFW;
                log.debug("Content mode is SFW (or default), using ZSET: {}", zsetKey);
            }
        }
        Optional<String> randomIdOptional = this.getRandomBookmarkIdWithWeight(zsetKey);

        if (randomIdOptional.isPresent()) {
            String randomId = randomIdOptional.get();
            log.debug("Randomly selected bookmark ID {} from ZSET {}", randomId, zsetKey);
            PixivBookmark bookmark = this.getById(randomId);
            return Optional.ofNullable(bookmark);
        } else {
            log.warn("无法从 Redis ZSET {} 中获取随机 Bookmark ID。该分类可能为空。", zsetKey);
            return Optional.empty();
        }
    }


    /**
     * 【已修改】使用加权随机算法从指定的 Redis ZSET 中获取一个 Bookmark ID。
     * @param zsetKey 要抽取的 Redis ZSET Key
     * @return 随机抽取的 Bookmark ID Optional
     */
    private Optional<String> getRandomBookmarkIdWithWeight(String zsetKey) {
        ZSetOperations<String, String> zSetOps = redisTemplate.opsForZSet();

        // 使用 Optional.ofNullable 处理可能为 null 的情况
        long zcard = Optional.of(zSetOps.zCard(zsetKey)).orElse(0L);
        if (zcard == 0) {
            log.warn("ZSET {} 为空，无法进行随机抽取。", zsetKey);
            return Optional.empty();
        }

        checkAndResetWeightsIfNeeded(zsetKey);

        String selectedId = zSetOps.randomMember(zsetKey);

        // 降低被选中 ID 的权重（惩罚）
        // 每次抽中，权重减 10，但最低不小于 1
        Double newScore = zSetOps.incrementScore(zsetKey, selectedId, -10.0);
        if (newScore < 1.0) {
            zSetOps.add(zsetKey, selectedId, 1.0); // 权重不低于1
        }

        return Optional.of(selectedId);
    }

    /**
     * 【已修改】检查并可能重置特定 ZSET 权重的辅助方法
     * @param zsetKey 要检查的 Redis ZSET Key
     */
    private void checkAndResetWeightsIfNeeded(String zsetKey) {
        // 为避免每次都计算总分，我们可以进行概率性检查，比如 1% 的几率
        if (random.nextInt(100) > 0) { // 99% 的情况直接跳过，降低性能开销
            return;
        }

        log.debug("Performing probabilistic weight check for ZSET: {}", zsetKey);
        ZSetOperations<String, String> zSetOps = redisTemplate.opsForZSet();

        // 获取ZSET中分数最高的成员
        Set<ZSetOperations.TypedTuple<String>> topMember = zSetOps.rangeWithScores(zsetKey, -1, -1);

        if (topMember == null || topMember.isEmpty()) {
            return;
        }

        double maxScore = topMember.iterator().next().getScore();

        // 如果最高分都低于阈值，说明整体权重过低，需要重置
        if (maxScore < WEIGHT_RESET_THRESHOLD) {
            log.warn("Max weight ({}) in ZSET {} is below threshold ({}). Triggering global weight reset.",
                    maxScore, zsetKey, WEIGHT_RESET_THRESHOLD);

            // 异步执行重置，避免阻塞当前请求
            CompletableFuture.runAsync(() -> {
                Set<String> allMembers = zSetOps.range(zsetKey, 0, -1);
                if (allMembers != null && !allMembers.isEmpty()) {
                    // 使用 pipeline 批量重置，性能更高
                    redisTemplate.executePipelined((RedisCallback<Object>) connection -> {
                        for (String memberId : allMembers) {
                            connection.zSetCommands().zAdd(zsetKey.getBytes(), INITIAL_WEIGHT, memberId.getBytes());
                        }
                        return null; // 返回 null 即可
                    });
                    log.info("Global weight reset completed for {} members in ZSET {}.", allMembers.size(), zsetKey);
                }
            });
        }
    }


    /**
     * 获取并缓存所有的 Bookmark ID 列表。
     * 这个方法的结果会被缓存，避免频繁查询数据库。
     */
    @Override
    public List<Long> getAllBookmarkIds() {
        log.debug("Fetching all bookmark IDs from Redis ZSET: {}", CacheConstants.ZSET_BOOKMARK_WEIGHTS_KEY_MIX);
        Set<String> idStrings = redisTemplate.opsForZSet().range(CacheConstants.ZSET_BOOKMARK_WEIGHTS_KEY_MIX, 0, -1);

        if (idStrings.isEmpty()) {
            return Collections.emptyList();
        }

        return idStrings.stream().map(Long::parseLong).collect(Collectors.toList());
    }


    @Deprecated
    public Optional<PixivBookmark> getRandomBookmark() {
        // 这是一个演示，你可以决定如何处理调用旧方法的地方
        // 比如，让它默认调用 SFW 模式
        log.warn("The method getRandomBookmark() without arguments is deprecated. Defaulting to SFW mode with dummy user/group IDs.");
        return getRandomBookmark(0L, 0L);
    }

}
