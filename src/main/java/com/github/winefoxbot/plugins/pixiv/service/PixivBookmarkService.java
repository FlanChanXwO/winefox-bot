package com.github.winefoxbot.plugins.pixiv.service;

import com.github.winefoxbot.plugins.pixiv.model.entity.PixivBookmark;
import com.baomidou.mybatisplus.extension.service.IService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
* @author FlanChan
* @description 针对表【pixiv_bookmark(存储特定用户 P站收藏作品的信息)】的数据库操作Service
* @createDate 2026-01-04 16:05:55
*/
public interface PixivBookmarkService extends IService<PixivBookmark> {

    /**
     * 同步 Pixiv 收藏夹中的作品。
     * 该方法会从 Pixiv 获取指定用户的收藏夹数据，并将其存储到数据库中。
     */
    @Transactional
    void syncBookmarks();

    /**
     * 定时同步 Pixiv 收藏夹中的作品。
     */
    @Scheduled(cron = "${pixiv.bookmark.tracker.full-cron}")
    void scheduleSyncBookmarks();

    /**
     * 同步最新的 Pixiv 收藏作品。
     */
    @Transactional(rollbackFor = Exception.class)
    void syncLatestBookmarks();

    /**
     * 定时同步最新的 Pixiv 收藏作品。
     */
    @Scheduled(cron = "${pixiv.bookmark.tracker.light-cron}")
    void scheduleSyncLatestBookmarks();
    /**
     * 从数据库中随机获取一条收藏记录。
     * @return 如果表中存在记录，则返回一个包含 PixivBookmark 的 Optional；否则返回 Optional.empty()。
     */
    Optional<PixivBookmark> getRandomBookmark();
}
