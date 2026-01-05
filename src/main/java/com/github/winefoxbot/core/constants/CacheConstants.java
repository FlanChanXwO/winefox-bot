package com.github.winefoxbot.core.constants;

/**
 * 缓存常量类
 * <p>
 * 用于统一管理项目中所有的缓存名称 (cache names)。
 * 命名规范建议: [业务模块]_[缓存内容]_[CACHE]
 * 如果有必要，可以在注释中注明该缓存的过期时间 (TTL)。
 * 最好注明该缓存的使用场景和作用，方便维护。
 * </p>
 */
public final class CacheConstants {


    private CacheConstants() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }

    /**
     * 缓存键前缀
     */
    public static final String CACHE_KEY_PREFIX = "winefoxbot:";

    // --- Pixiv 插件模块 ---
    /**
     * Pixiv 作品信息缓存
     * TTL: 2 小时 (在 CacheConfig 中配置)
     */
    public static final String PIXIV_ARTWORK_INFO_CACHE = "pixiv:artwork:info";

    /**
     * Pixiv 收藏夹作品权重有序集合键（在PixivBookmarkServiceImpl配置）
     * TTL: 30 天
     */
    public static final String ZSET_BOOKMARK_WEIGHTS_KEY_SFW = CACHE_KEY_PREFIX  +"pixiv:bookmark:weights:sfw";
    public static final String ZSET_BOOKMARK_WEIGHTS_KEY_R18 = CACHE_KEY_PREFIX  + "pixiv:bookmark:weights:r18";
    public static final String ZSET_BOOKMARK_WEIGHTS_KEY_MIX = CACHE_KEY_PREFIX  + "pixiv:bookmark:weights:mix";

}
